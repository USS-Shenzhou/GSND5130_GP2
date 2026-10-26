import csv
import math
import random
import statistics
import time
from collections import deque
from dataclasses import dataclass
from pathlib import Path

from maze_to_schem import read_wall_mask

SCRIPT_DIRECTORY = Path(__file__).resolve().parent
INPUT_DIRECTORY = SCRIPT_DIRECTORY / "output" / "filtered"
INPUT_PATTERN = "filtered_65px_seed*.png"

BRANCH_LOOKAHEAD_STEPS = 12
WALK_LOOKAHEAD_STEPS = [12]
WALK_RUNS = 1000
WALK_SEED = 0

OUTPUT_CSV = INPUT_DIRECTORY / f"filtered_65px_metrics_walk{'_'.join(str(steps) for steps in WALK_LOOKAHEAD_STEPS)}.csv"

UP = (0, -1)
RIGHT = (1, 0)
DOWN = (0, 1)
LEFT = (-1, 0)
DIRECTIONS = [UP, RIGHT, DOWN, LEFT]


@dataclass
class MazeTree:
    start: tuple
    goal: tuple
    neighbors: dict
    parent: dict
    children: dict
    height: dict
    distance: dict
    solution: list
    solution_nodes: set


def build_pixel_graph(wall_mask, image_width, image_height):
    neighbors = {}
    for y in range(image_height):
        for x in range(image_width):
            if wall_mask[y][x]:
                continue
            neighbors[(x, y)] = [
                (x + dx, y + dy)
                for dx, dy in DIRECTIONS
                if 0 <= x + dx < image_width and 0 <= y + dy < image_height and not wall_mask[y + dy][x + dx]
            ]
    return neighbors


def build_maze_tree(neighbors, start, goal):
    parent = {start: None}
    distance = {start: 0}
    order = [start]
    queue = deque([start])
    while queue:
        node = queue.popleft()
        for neighbor in neighbors[node]:
            if neighbor not in parent:
                parent[neighbor] = node
                distance[neighbor] = distance[node] + 1
                order.append(neighbor)
                queue.append(neighbor)

    children = {node: [] for node in order}
    for node in order[1:]:
        children[parent[node]].append(node)

    height = {}
    for node in reversed(order):
        height[node] = max((height[child] + 1 for child in children[node]), default=0)

    solution = [goal]
    while solution[-1] != start:
        solution.append(parent[solution[-1]])
    solution.reverse()

    return MazeTree(start, goal, neighbors, parent, children, height, distance, solution, set(solution))


def branch_options(maze, lookahead_steps):
    # Branches are judged as entered from the start side. A branch that does not lead to the goal and ends
    # entirely within the lookahead distance is ruled out at a glance.
    preferred_options = {}
    other_options = {}
    for node, children in maze.children.items():
        options = [child for child in children if child in maze.solution_nodes or maze.height[child] + 1 > lookahead_steps]
        preferred_options[node] = [child for child in options if child[1] < node[1] or child[0] > node[0]]
        other_options[node] = [child for child in options if child[1] > node[1] or child[0] < node[0]]
    return preferred_options, other_options


def count_dead_ends(maze):
    return sum(1 for node, adjacent in maze.neighbors.items() if len(adjacent) == 1 and node not in (maze.start, maze.goal))


def count_major_branch_points(maze):
    preferred_options, other_options = branch_options(maze, BRANCH_LOOKAHEAD_STEPS)
    return sum(1 for node in maze.children if len(preferred_options[node]) + len(other_options[node]) >= 2)


def count_bfs_expanded_nodes(maze):
    visited = {maze.start}
    queue = deque([maze.start])
    expanded = 0
    while queue:
        node = queue.popleft()
        expanded += 1
        if node == maze.goal:
            break
        for neighbor in maze.neighbors[node]:
            if neighbor not in visited:
                visited.add(neighbor)
                queue.append(neighbor)
    return expanded


def simulate_human_walk(maze, preferred_options, other_options, rng):
    # Depth-first walk that remembers tried branches, prefers up/right (towards the goal), picks randomly on ties,
    # and turns back as soon as everything ahead is a dead end within the lookahead distance.
    remaining = {}
    position = maze.start
    steps = 0
    while position != maze.goal:
        if position not in remaining:
            remaining[position] = (list(preferred_options[position]), list(other_options[position]))
        preferred, others = remaining[position]
        candidates = preferred if preferred else others
        if candidates:
            position = candidates.pop(rng.randrange(len(candidates)))
        else:
            position = maze.parent[position]
        steps += 1
    return steps


def average_walk_length(maze, lookahead_steps):
    preferred_options, other_options = branch_options(maze, lookahead_steps)
    rng = random.Random(WALK_SEED)
    return statistics.fmean(simulate_human_walk(maze, preferred_options, other_options, rng) for _ in range(WALK_RUNS))


def build_axial_graph(neighbors):
    # Axial lines are the maximal straight runs of walkable pixels; a horizontal and a vertical line are connected
    # when they share a pixel.
    lines = []
    for x, y in neighbors:
        for dx, dy in (RIGHT, DOWN):
            if (x - dx, y - dy) not in neighbors and (x + dx, y + dy) in neighbors:
                line = []
                line_x, line_y = x, y
                while (line_x, line_y) in neighbors:
                    line.append((line_x, line_y))
                    line_x, line_y = line_x + dx, line_y + dy
                lines.append(line)

    lines_of_pixel = {}
    for line_index, line in enumerate(lines):
        for pixel in line:
            lines_of_pixel.setdefault(pixel, []).append(line_index)

    line_neighbors = [set() for _ in lines]
    for line_indices in lines_of_pixel.values():
        if len(line_indices) == 2:
            first, second = line_indices
            line_neighbors[first].add(second)
            line_neighbors[second].add(first)

    return lines_of_pixel, line_neighbors


def axial_depths(line_neighbors, source_lines):
    depth = {line: 0 for line in source_lines}
    queue = deque(source_lines)
    while queue:
        line = queue.popleft()
        for neighbor in line_neighbors[line]:
            if neighbor not in depth:
                depth[neighbor] = depth[line] + 1
                queue.append(neighbor)
    return depth


def space_syntax_measures(maze):
    lines_of_pixel, line_neighbors = build_axial_graph(maze.neighbors)
    line_count = len(line_neighbors)
    connectivity = [len(adjacent) for adjacent in line_neighbors]

    # Global integration normalized with Hillier & Hanson's diamond value.
    diamond_value = 2 * (line_count * (math.log2((line_count + 2) / 3) - 1) + 1) / ((line_count - 1) * (line_count - 2))
    integration = []
    for line in range(line_count):
        mean_depth = sum(axial_depths(line_neighbors, [line]).values()) / (line_count - 1)
        relative_asymmetry = 2 * (mean_depth - 1) / (line_count - 2)
        integration.append(diamond_value / relative_asymmetry)

    depth_from_start = axial_depths(line_neighbors, lines_of_pixel[maze.start])
    start_goal_depth = min(depth_from_start[line] for line in lines_of_pixel[maze.goal])

    return start_goal_depth, statistics.fmean(connectivity), statistics.fmean(integration), statistics.correlation(connectivity, integration)


def main():
    image_paths = sorted(INPUT_DIRECTORY.glob(INPUT_PATTERN), key=lambda path: (len(path.name), path.name))
    rows = []
    start_time = time.perf_counter()

    for index, image_path in enumerate(image_paths):
        image_width, image_height, wall_mask = read_wall_mask(image_path)
        neighbors = build_pixel_graph(wall_mask, image_width, image_height)
        maze = build_maze_tree(neighbors, (1, image_height - 2), (image_width - 2, 1))

        start_goal_depth, mean_connectivity, mean_integration, intelligibility = space_syntax_measures(maze)

        row = {
            "迷宫": image_path.stem,
            "正确路径长度(步)": maze.distance[maze.goal],
            "死胡同数量": count_dead_ends(maze),
            "重大分叉点数量": count_major_branch_points(maze),
            "BFS扩展节点数": count_bfs_expanded_nodes(maze),
        }
        for lookahead_steps in WALK_LOOKAHEAD_STEPS:
            row[f"简单拟人寻路总长度(阈值{lookahead_steps}步,{WALK_RUNS}次平均)"] = round(average_walk_length(maze, lookahead_steps), 1)
        row.update({
            "空间句法-起点到终点深度": start_goal_depth,
            "空间句法-平均连接度": round(mean_connectivity, 4),
            "空间句法-平均整合度(HH)": round(mean_integration, 4),
            "空间句法-可理解度(r)": round(intelligibility, 4),
        })
        rows.append(row)

        elapsed = time.perf_counter() - start_time
        estimated_remaining = elapsed / (index + 1) * (len(image_paths) - index - 1)
        print(f"[{index + 1}/{len(image_paths)}] {image_path.name}  elapsed {elapsed:.1f}s  ETA {estimated_remaining:.1f}s")

    with open(OUTPUT_CSV, "w", newline="", encoding="utf-8-sig") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    print(OUTPUT_CSV)


if __name__ == "__main__":
    main()
