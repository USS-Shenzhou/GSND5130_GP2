import random
import struct
import time
import zlib
from pathlib import Path

IMAGE_WIDTH = 65
IMAGE_HEIGHT = 65
MAZE_WIDTH = (IMAGE_WIDTH - 1) // 2
MAZE_HEIGHT = (IMAGE_HEIGHT - 1) // 2
MAZE_COUNT = 10
OUTPUT_DIRECTORY = Path(__file__).parent / "output" / "wilson"

WALL = 0
PATH = 255


def build_neighbors(width, height):
    neighbors = []
    for y in range(height):
        for x in range(width):
            cell_neighbors = []
            if x > 0:
                cell_neighbors.append(y * width + x - 1)
            if x < width - 1:
                cell_neighbors.append(y * width + x + 1)
            if y > 0:
                cell_neighbors.append((y - 1) * width + x)
            if y < height - 1:
                cell_neighbors.append((y + 1) * width + x)
            neighbors.append(cell_neighbors)
    return neighbors


def generate_wilson_maze(cell_count, neighbors, rng):
    in_tree = [False] * cell_count
    next_cell = [-1] * cell_count
    passages = []

    in_tree[rng.randrange(cell_count)] = True
    walk_starts = list(range(cell_count))
    rng.shuffle(walk_starts)

    for start in walk_starts:
        if in_tree[start]:
            continue

        # Random walk until hitting the tree. Overwriting next_cell on revisits erases loops implicitly.
        current = start
        while not in_tree[current]:
            following = rng.choice(neighbors[current])
            next_cell[current] = following
            current = following

        current = start
        while not in_tree[current]:
            in_tree[current] = True
            passages.append((current, next_cell[current]))
            current = next_cell[current]

    return passages


def render_maze(image_width, image_height, maze_width, maze_height, passages):
    # The maze occupies (2 * maze_size + 1) pixels; any leftover row or column on the right/bottom stays wall.
    pixels = bytearray([WALL]) * (image_width * image_height)

    for y in range(maze_height):
        for x in range(maze_width):
            pixels[(2 * y + 1) * image_width + 2 * x + 1] = PATH

    for from_cell, to_cell in passages:
        pixel_x = from_cell % maze_width + to_cell % maze_width + 1
        pixel_y = from_cell // maze_width + to_cell // maze_width + 1
        pixels[pixel_y * image_width + pixel_x] = PATH

    return pixels


def png_chunk(chunk_type, data):
    checksum = zlib.crc32(chunk_type + data) & 0xFFFFFFFF
    return struct.pack(">I", len(data)) + chunk_type + data + struct.pack(">I", checksum)


def write_grayscale_png(path, image_width, image_height, pixels):
    raw_rows = bytearray()
    for row in range(image_height):
        raw_rows.append(0)
        raw_rows.extend(pixels[row * image_width:(row + 1) * image_width])

    header = struct.pack(">IIBBBBB", image_width, image_height, 8, 0, 0, 0, 0)
    with open(path, "wb") as file:
        file.write(b"\x89PNG\r\n\x1a\n")
        file.write(png_chunk(b"IHDR", header))
        file.write(png_chunk(b"IDAT", zlib.compress(bytes(raw_rows), 9)))
        file.write(png_chunk(b"IEND", b""))


def main():
    OUTPUT_DIRECTORY.mkdir(parents=True, exist_ok=True)
    neighbors = build_neighbors(MAZE_WIDTH, MAZE_HEIGHT)
    cell_count = MAZE_WIDTH * MAZE_HEIGHT
    start_time = time.perf_counter()

    for index in range(MAZE_COUNT):
        rng = random.Random(index)
        passages = generate_wilson_maze(cell_count, neighbors, rng)
        pixels = render_maze(IMAGE_WIDTH, IMAGE_HEIGHT, MAZE_WIDTH, MAZE_HEIGHT, passages)
        output_path = OUTPUT_DIRECTORY / f"wilson_{IMAGE_WIDTH}px_{index:02d}.png"
        write_grayscale_png(output_path, IMAGE_WIDTH, IMAGE_HEIGHT, pixels)

        elapsed = time.perf_counter() - start_time
        remaining = elapsed / (index + 1) * (MAZE_COUNT - index - 1)
        print(f"[{index + 1}/{MAZE_COUNT}] {output_path.name}  elapsed {elapsed:.1f}s  ETA {remaining:.1f}s")


if __name__ == "__main__":
    main()
