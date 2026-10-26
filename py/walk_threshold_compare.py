import time
from pathlib import Path

from maze_metrics import average_walk_length, build_maze_tree, build_pixel_graph
from maze_to_schem import read_wall_mask

MAZE_DIRECTORY = Path(__file__).resolve().parent / "output" / "wilson"
MAZE_NAMES = ["wilson_65px_00", "wilson_65px_01"]
PLAYER_SECONDS = [95, 143]
THRESHOLDS = range(1, 33)


def load_maze(name):
    image_width, image_height, wall_mask = read_wall_mask(MAZE_DIRECTORY / f"{name}.png")
    neighbors = build_pixel_graph(wall_mask, image_width, image_height)
    return build_maze_tree(neighbors, (1, image_height - 2), (image_width - 2, 1))


def main():
    mazes = [load_maze(name) for name in MAZE_NAMES]
    target_ratio = PLAYER_SECONDS[0] / PLAYER_SECONDS[1]
    print(f"player time ratio {PLAYER_SECONDS[0]}:{PLAYER_SECONDS[1]} = {target_ratio:.4f}")

    results = []
    start_time = time.perf_counter()
    for index, threshold in enumerate(THRESHOLDS):
        lengths = [average_walk_length(maze, threshold) for maze in mazes]
        ratio = lengths[0] / lengths[1]
        results.append((threshold, lengths, ratio))

        elapsed = time.perf_counter() - start_time
        estimated_remaining = elapsed / (index + 1) * (len(THRESHOLDS) - index - 1)
        print(f"threshold {threshold:2d}  {lengths[0]:7.1f}  {lengths[1]:7.1f}  ratio {ratio:.4f}  elapsed {elapsed:.1f}s  ETA {estimated_remaining:.1f}s")

    best_threshold, best_lengths, best_ratio = min(results, key=lambda result: abs(result[2] - target_ratio))
    print(f"closest: threshold {best_threshold}  ratio {best_ratio:.4f}  ({best_lengths[0]:.1f} : {best_lengths[1]:.1f})")


if __name__ == "__main__":
    main()
