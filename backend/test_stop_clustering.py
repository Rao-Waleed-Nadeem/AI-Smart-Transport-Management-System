from data_pipeline.student_loader import load_student_stops
from data_pipeline.stop_builder import snap_stops_to_roads, merge_nearby_stops, update_student_stops
from osm.fetch_roads import fetch_or_load_road_graph
from osm.road_filter import filter_main_roads
from clustering.stop_clustering import cluster_stops
import folium

stops = load_student_stops()[:50]
graph = fetch_or_load_road_graph()
main_graph = filter_main_roads(graph)

if main_graph:
    snapped = snap_stops_to_roads(stops, main_graph)
    merged = merge_nearby_stops(snapped)
    clusters = cluster_stops(merged)
    
    update_student_stops(merged)

    m = folium.Map(location=[31.60253, 73.03485], zoom_start=12, tiles="cartodbpositron")

    colors = ['red', 'blue', 'green', 'purple', 'orange', 'darkred', 'cadetblue', 'darkblue']

    for bus_id, stops_list in clusters.items():
        color = colors[bus_id % len(colors)]
        for s in stops_list:
            folium.Marker(
                [s['lat'], s['lng']],
                popup=f"Bus {bus_id}<br>{s.get('name')} × {s['student_count']}",
                icon=folium.Icon(color=color, icon="bus")
            ).add_to(m)

    m.save("step14_bus_clusters.html")
    print("Saved: step14_bus_clusters.html → different colors = different buses")