from data_pipeline.student_loader import load_student_stops
from data_pipeline.stop_builder import snap_stops_to_roads, merge_nearby_stops
from osm.fetch_roads import fetch_or_load_road_graph
from osm.road_filter import filter_main_roads
import folium
from config.logging_config import logger

# Load data (use more stops to see merging effect)
stops = load_student_stops()[:40]
graph = fetch_or_load_road_graph()
main_graph = filter_main_roads(graph)

if main_graph is None or not stops:
    print("Cannot proceed")
else:
    snapped = snap_stops_to_roads(stops, main_graph)
    merged = merge_nearby_stops(snapped)

    # ─── Create map ───
    m = folium.Map(location=[31.60253, 73.03485], zoom_start=13, tiles="cartodbpositron")

    # Show merged stops (purple markers + student count)
    for stop in merged:
        count = stop['student_count']
        label = f"{stop.get('name', 'Group')} × {count}"
        if count > 1:
            label += f"<br>Merged from {len(stop.get('merged_student_ids', []))} students"

        folium.Marker(
            [stop['lat'], stop['lng']],
            popup=label,
            tooltip=f"Stop serving {count} students",
            icon=folium.Icon(color="purple", icon="users", prefix="fa")
        ).add_to(m)

    m.save("step11_merged_stops.html")
    print(f"Saved: step11_merged_stops.html")
    print(f"Before merge: {len(snapped)} stops")
    print(f"After merge : {len(merged)} stops")