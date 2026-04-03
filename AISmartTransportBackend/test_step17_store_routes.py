# test_step17_store_routes.py
from data_pipeline.student_loader import load_student_stops
from data_pipeline.stop_builder import snap_stops_to_roads, merge_nearby_stops
from osm.fetch_roads import fetch_or_load_road_graph
from osm.road_filter import filter_main_roads
from clustering.stop_clustering import cluster_stops
from optimization.store_routes import store_optimized_routes
import folium
from config.settings import UNIVERSITY_COORD

# Full pipeline
stops = load_student_stops()[:50]  # or all
graph = fetch_or_load_road_graph()
main_graph = filter_main_roads(graph)
snapped = snap_stops_to_roads(stops, main_graph)
merged = merge_nearby_stops(snapped)
clusters = cluster_stops(merged)

# Store everything
store_optimized_routes(clusters)

# ─── Quick map to confirm ───
m = folium.Map(location=[UNIVERSITY_COORD[0], UNIVERSITY_COORD[1]], zoom_start=11, tiles="cartodbpositron")

# ... add route lines & markers like previous test if needed ...

m.save("test_step17_stored_routes.html")
print("Stored routes & stops in Firestore")
print("Check Firebase console: routes & stops collections")
print("Map saved: test_step17_stored_routes.html")