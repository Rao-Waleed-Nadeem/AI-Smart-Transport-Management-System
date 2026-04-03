# test_road_graph.py (temporary)
from osm.fetch_roads import fetch_or_load_road_graph
from config.logging_config import logger

graph = fetch_or_load_road_graph(force_redownload=False)

if graph is not None:
    print(f"Graph loaded successfully!")
    print(f" → Nodes: {len(graph.nodes())}")
    print(f" → Edges: {len(graph.edges())}")
    print(f" → Example node: {next(iter(graph.nodes(data=True)))}")
else:
    print("Failed to get road graph — check logs")