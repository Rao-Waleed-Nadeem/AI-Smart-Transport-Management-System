# test_step8_filter_only.py
import folium
import osmnx as ox
from osm.fetch_roads import fetch_or_load_road_graph
from osm.road_filter import filter_main_roads
from config.logging_config import logger

# ────────────────────────────────────────────────
# Center map on University Faisalabad
CENTER = [31.60253, 73.03485]
ZOOM = 12

def plot_main_roads_on_folium(graph):
    """Manually plot graph edges on Folium map"""
    m = folium.Map(location=CENTER, zoom_start=ZOOM, tiles="cartodbpositron")

    # Add edges as lines
    for u, v, key, data in graph.edges(keys=True, data=True):
        if 'geometry' in data:
            # If edge has geometry (curved road)
            coords = [(lat, lng) for lng, lat in data['geometry'].coords]  # OSMnx uses (lng, lat)
        else:
            # Straight line between nodes
            u_coord = (graph.nodes[u]['y'], graph.nodes[u]['x'])
            v_coord = (graph.nodes[v]['y'], graph.nodes[v]['x'])
            coords = [u_coord, v_coord]

        folium.PolyLine(
            locations=coords,
            color="#1976D2",
            weight=2.5,
            opacity=0.7,
            tooltip=data.get('name', 'Unnamed road')
        ).add_to(m)

    return m

# ────────────────────────────────────────────────
graph = fetch_or_load_road_graph()
main_graph = filter_main_roads(graph)

if main_graph is None or len(main_graph.edges) == 0:
    print("No main roads found after filtering")
else:
    print(f"Main roads filtered successfully!")
    print(f" → Nodes: {len(main_graph.nodes())}")
    print(f" → Edges: {len(main_graph.edges())}")

    # Generate interactive map
    m = plot_main_roads_on_folium(main_graph)

    # Optional: add university marker
    folium.Marker(
        CENTER,
        popup="University Location",
        icon=folium.Icon(color="blue", icon="university", prefix="fa")
    ).add_to(m)

    output_file = "step8_main_roads_map.html"
    m.save(output_file)
    print(f"\nInteractive map saved → open in browser:")
    print(f"  {output_file}")