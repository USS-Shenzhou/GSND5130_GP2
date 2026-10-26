import gzip
import random
import struct
import zlib
from pathlib import Path

SCRIPT_DIRECTORY = Path(__file__).resolve().parent
INPUT_IMAGES = [
    SCRIPT_DIRECTORY / "output" / "wilson" / "wilson_65px_03.png",
]
OUTPUT_DIRECTORY = SCRIPT_DIRECTORY.parent / "run" / "config" / "worldedit" / "schematics"

WALL_THICKNESS = 1
PATH_WIDTH = 5
WALL_HEIGHT = 2
WALL_THRESHOLD = 128
WALL_BLOCK_SEED = 0

AIR = "minecraft:air"
WALL_BLOCKS = [
    ("minecraft:stone_bricks", 50),
    ("minecraft:cracked_stone_bricks", 10),
    ("minecraft:mossy_stone_bricks", 25),
    ("minecraft:chiseled_stone_bricks", 5),
    ("minecraft:tuff_bricks", 10),
]
PALETTE = [AIR] + [block_id for block_id, _ in WALL_BLOCKS]

# SharedConstants.WORLD_VERSION of Minecraft 26.2.
DATA_VERSION = 4903
SPONGE_SCHEMATIC_VERSION = 3

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
CHANNELS_BY_COLOR_TYPE = {0: 1, 2: 3, 4: 2, 6: 4}

TAG_END = 0
TAG_SHORT = 2
TAG_INT = 3
TAG_BYTE_ARRAY = 7
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11


def paeth_predictor(left, up, upper_left):
    estimate = left + up - upper_left
    distance_left = abs(estimate - left)
    distance_up = abs(estimate - up)
    distance_upper_left = abs(estimate - upper_left)
    if distance_left <= distance_up and distance_left <= distance_upper_left:
        return left
    if distance_up <= distance_upper_left:
        return up
    return upper_left


def unfilter_row(filter_type, row, previous_row, bytes_per_pixel):
    if filter_type == 0:
        return
    for index in range(len(row)):
        left = row[index - bytes_per_pixel] if index >= bytes_per_pixel else 0
        up = previous_row[index]
        upper_left = previous_row[index - bytes_per_pixel] if index >= bytes_per_pixel else 0
        if filter_type == 1:
            predictor = left
        elif filter_type == 2:
            predictor = up
        elif filter_type == 3:
            predictor = (left + up) // 2
        elif filter_type == 4:
            predictor = paeth_predictor(left, up, upper_left)
        else:
            raise ValueError(f"Unknown PNG filter type {filter_type}")
        row[index] = (row[index] + predictor) & 0xFF


def read_wall_mask(path):
    data = path.read_bytes()
    if data[:8] != PNG_SIGNATURE:
        raise ValueError(f"{path} is not a PNG file")

    compressed = bytearray()
    position = 8
    while position < len(data):
        length, chunk_type = struct.unpack(">I4s", data[position:position + 8])
        chunk_data = data[position + 8:position + 8 + length]
        position += 12 + length
        if chunk_type == b"IHDR":
            image_width, image_height, bit_depth, color_type, _, _, interlace = struct.unpack(">IIBBBBB", chunk_data)
        elif chunk_type == b"IDAT":
            compressed.extend(chunk_data)
        elif chunk_type == b"IEND":
            break

    if bit_depth != 8 or color_type not in CHANNELS_BY_COLOR_TYPE or interlace != 0:
        raise ValueError(f"{path}: only non-interlaced 8-bit grayscale or RGB(A) PNG is supported")

    channels = CHANNELS_BY_COLOR_TYPE[color_type]
    stride = image_width * channels
    raw = zlib.decompress(bytes(compressed))
    previous_row = bytearray(stride)
    wall_mask = []
    for row_index in range(image_height):
        start = row_index * (stride + 1)
        row = bytearray(raw[start + 1:start + 1 + stride])
        unfilter_row(raw[start], row, previous_row, channels)
        wall_mask.append([row[x * channels] < WALL_THRESHOLD for x in range(image_width)])
        previous_row = row

    return image_width, image_height, wall_mask


def expand_axis(pixel_count):
    # In the generated images, even pixel indices are wall lines and odd ones are cell lines.
    source_pixels = []
    for pixel in range(pixel_count):
        span = WALL_THICKNESS if pixel % 2 == 0 else PATH_WIDTH
        source_pixels.extend([pixel] * span)
    return source_pixels


def build_block_data(wall_mask, image_width, image_height, rng):
    source_columns = expand_axis(image_width)
    source_rows = expand_axis(image_height)
    air_index = PALETTE.index(AIR)
    wall_indices = [PALETTE.index(block_id) for block_id, _ in WALL_BLOCKS]
    wall_weights = [weight for _, weight in WALL_BLOCKS]

    # Blocks are ordered by x + z * width + y * width * length.
    # Each palette index is stored as a varint; all indices here are below 128, so each one is a single byte.
    block_data = bytearray()
    for _ in range(WALL_HEIGHT):
        for pixel_row in source_rows:
            row_mask = wall_mask[pixel_row]
            for pixel_column in source_columns:
                if row_mask[pixel_column]:
                    block_data.append(rng.choices(wall_indices, wall_weights)[0])
                else:
                    block_data.append(air_index)

    return len(source_columns), len(source_rows), block_data


def start_block_position(image_width, image_height):
    # The start is the bottom-left cell of the image; the paste origin goes to the middle of that cell.
    start_x = expand_axis(image_width).index(1) + PATH_WIDTH // 2
    start_z = expand_axis(image_height).index(image_height - 2) + PATH_WIDTH // 2
    return start_x, start_z


def nbt_string(text):
    encoded = text.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def nbt_unsigned_short(value):
    return TAG_SHORT, struct.pack(">H", value)


def nbt_int(value):
    return TAG_INT, struct.pack(">i", value)


def nbt_byte_array(data):
    return TAG_BYTE_ARRAY, struct.pack(">i", len(data)) + bytes(data)


def nbt_int_array(values):
    return TAG_INT_ARRAY, struct.pack(f">i{len(values)}i", len(values), *values)


def nbt_compound(entries):
    payload = bytearray()
    for name, (tag_type, tag_payload) in entries.items():
        payload.append(tag_type)
        payload += nbt_string(name)
        payload += tag_payload
    payload.append(TAG_END)
    return TAG_COMPOUND, bytes(payload)


def build_schematic(width, length, block_data, paste_origin):
    # WorldEdit pastes schematic block (0, 0, 0) at the player's position plus Offset,
    # so a negated position puts that block under the player.
    schematic = nbt_compound({
        "Version": nbt_int(SPONGE_SCHEMATIC_VERSION),
        "DataVersion": nbt_int(DATA_VERSION),
        "Width": nbt_unsigned_short(width),
        "Height": nbt_unsigned_short(WALL_HEIGHT),
        "Length": nbt_unsigned_short(length),
        "Offset": nbt_int_array([-coordinate for coordinate in paste_origin]),
        "Blocks": nbt_compound({
            "Palette": nbt_compound({block_id: nbt_int(index) for index, block_id in enumerate(PALETTE)}),
            "Data": nbt_byte_array(block_data),
        }),
    })

    # Sponge schematic v3 wraps the "Schematic" compound in an unnamed root compound.
    root_type, root_payload = nbt_compound({"Schematic": schematic})
    return bytes([root_type]) + nbt_string("") + root_payload


def main():
    OUTPUT_DIRECTORY.mkdir(parents=True, exist_ok=True)
    for input_image in INPUT_IMAGES:
        image_width, image_height, wall_mask = read_wall_mask(input_image)
        width, length, block_data = build_block_data(wall_mask, image_width, image_height, random.Random(WALL_BLOCK_SEED))
        start_x, start_z = start_block_position(image_width, image_height)

        output_path = OUTPUT_DIRECTORY / f"{input_image.stem}_path{PATH_WIDTH}_height{WALL_HEIGHT}.schem"
        output_path.write_bytes(gzip.compress(build_schematic(width, length, block_data, [start_x, 0, start_z])))
        print(f"{output_path}  {width} x {WALL_HEIGHT} x {length} (X x Y x Z)  paste origin ({start_x}, 0, {start_z})")


if __name__ == "__main__":
    main()
