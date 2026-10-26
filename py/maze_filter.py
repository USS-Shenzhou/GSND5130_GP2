import csv
import os
import random
import time
from collections import Counter
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

from maze_metrics import (
    WALK_RUNS,
    average_walk_length,
    build_maze_tree,
    build_pixel_graph,
    count_bfs_expanded_nodes,
    count_dead_ends,
    count_major_branch_points,
    space_syntax_measures,
)
from wilson_maze import IMAGE_HEIGHT, IMAGE_WIDTH, MAZE_HEIGHT, MAZE_WIDTH, WALL, build_neighbors, generate_wilson_maze, render_maze, write_grayscale_png

OUTPUT_DIRECTORY = Path(__file__).resolve().parent / "output" / "filtered"
OUTPUT_CSV = OUTPUT_DIRECTORY / f"filtered_{IMAGE_WIDTH}px_metrics.csv"

TARGET_COUNT = 10
TOLERANCE = 0.05
WALK_LOOKAHEAD_STEPS = 12
BATCH_SIZE = 8192

TARGETS = {
    "path_length": 260,
    "dead_ends": 290,
    "branch_points": 21,
    "bfs_nodes": 1700,
    "walk_length": 400,
    "start_goal_depth": 75,
    "mean_integration": 0.17,
    "intelligibility": 0.11,
}
CSV_HEADERS = {
    "path_length": "正确路径长度(步)",
    "dead_ends": "死胡同数量",
    "branch_points": "重大分叉点数量",
    "bfs_nodes": "BFS扩展节点数",
    "walk_length": f"简单拟人寻路总长度(阈值{WALK_LOOKAHEAD_STEPS}步,{WALK_RUNS}次平均)",
    "start_goal_depth": "空间句法-起点到终点深度",
    "mean_integration": "空间句法-平均整合度(HH)",
    "intelligibility": "空间句法-可理解度(r)",
}

CELL_NEIGHBORS = build_neighbors(MAZE_WIDTH, MAZE_HEIGHT)


def space_syntax_stage(maze):
    start_goal_depth, _, mean_integration, intelligibility = space_syntax_measures(maze)
    return {"start_goal_depth": start_goal_depth, "mean_integration": mean_integration, "intelligibility": intelligibility}


# Cheap measures come first so that most mazes are rejected before the expensive ones are computed.
STAGES = [
    lambda maze: {"path_length": maze.distance[maze.goal]},
    lambda maze: {"bfs_nodes": count_bfs_expanded_nodes(maze)},
    lambda maze: {"branch_points": count_major_branch_points(maze)},
    lambda maze: {"dead_ends": count_dead_ends(maze)},
    space_syntax_stage,
    lambda maze: {"walk_length": average_walk_length(maze, WALK_LOOKAHEAD_STEPS)},
]


def maze_name(seed):
    return f"filtered_{IMAGE_WIDTH}px_seed{seed}"


def render_seed(seed):
    passages = generate_wilson_maze(MAZE_WIDTH * MAZE_HEIGHT, CELL_NEIGHBORS, random.Random(seed))
    return render_maze(IMAGE_WIDTH, IMAGE_HEIGHT, MAZE_WIDTH, MAZE_HEIGHT, passages)


def evaluate_seed(seed):
    pixels = render_seed(seed)
    wall_mask = [[pixels[y * IMAGE_WIDTH + x] == WALL for x in range(IMAGE_WIDTH)] for y in range(IMAGE_HEIGHT)]
    neighbors = build_pixel_graph(wall_mask, IMAGE_WIDTH, IMAGE_HEIGHT)
    maze = build_maze_tree(neighbors, (1, IMAGE_HEIGHT - 2), (IMAGE_WIDTH - 2, 1))

    metrics = {}
    for stage in STAGES:
        for name, value in stage(maze).items():
            if abs(value - TARGETS[name]) > TOLERANCE * TARGETS[name]:
                return seed, name, None
            metrics[name] = value
    return seed, None, metrics


def main():
    OUTPUT_DIRECTORY.mkdir(parents=True, exist_ok=True)
    worker_count = min(61, os.cpu_count() or 1)
    chunk_size = max(1, BATCH_SIZE // (worker_count * 4))
    rejected_by = Counter()
    accepted = []
    generated = 0
    print(f"workers {worker_count}", flush=True)
    start_time = time.perf_counter()

    with ProcessPoolExecutor(max_workers=worker_count) as executor:
        while len(accepted) < TARGET_COUNT:
            for seed, failed_metric, metrics in executor.map(evaluate_seed, range(generated, generated + BATCH_SIZE), chunksize=chunk_size):
                generated += 1
                if failed_metric is not None:
                    rejected_by[failed_metric] += 1
                    continue
                accepted.append((seed, metrics))
                write_grayscale_png(OUTPUT_DIRECTORY / f"{maze_name(seed)}.png", IMAGE_WIDTH, IMAGE_HEIGHT, render_seed(seed))
                print(f"accepted {len(accepted)}/{TARGET_COUNT}: seed {seed}  after {generated} mazes  {time.perf_counter() - start_time:.1f}s", flush=True)
                if len(accepted) == TARGET_COUNT:
                    break

            elapsed = time.perf_counter() - start_time
            estimate = f"ETA {elapsed / len(accepted) * (TARGET_COUNT - len(accepted)):.0f}s" if accepted else "ETA unknown"
            print(f"generated {generated}  accepted {len(accepted)}  elapsed {elapsed:.1f}s  {generated / elapsed:.0f} mazes/s  {estimate}", flush=True)
        executor.shutdown(cancel_futures=True)

    print(f"{len(accepted)} accepted out of {generated} mazes in {elapsed:.1f}s", flush=True)
    for name, count in rejected_by.most_common():
        print(f"  first failed on {name}: {count}", flush=True)

    with open(OUTPUT_CSV, "w", newline="", encoding="utf-8-sig") as file:
        writer = csv.writer(file)
        writer.writerow(["迷宫", "种子", *CSV_HEADERS.values()])
        for seed, metrics in accepted:
            writer.writerow([maze_name(seed), seed, *(round(metrics[name], 4) for name in TARGETS)])
    print(OUTPUT_CSV, flush=True)


if __name__ == "__main__":
    main()
