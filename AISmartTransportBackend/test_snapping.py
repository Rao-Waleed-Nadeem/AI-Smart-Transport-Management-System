import folium
from osm.fetch_roads import fetch_or_load_road_graph
from osm.road_filter import filter_main_roads
from data_pipeline.student_loader import load_student_stops
from data_pipeline.stop_builder import snap_stops_to_roads
from config.logging_config import logger

stops = load_student_stops()[:20]           # first 20 to keep map clean
graph = fetch_or_load_road_graph()
main_graph = filter_main_roads(graph)

if main_graph is None or not stops:
    print("Cannot proceed — graph or stops missing")
else:
    snapped = snap_stops_to_roads(stops, main_graph)
    
    m = folium.Map(location=[31.60253, 73.03485], zoom_start=13, tiles="cartodbpositron")

    # Original stops – red
    for s in stops:
        folium.Marker(
            [s['lat'], s['lng']],
            popup=f"{s['name']} ({s['roll_no']})<br>Original",
            icon=folium.Icon(color="red", icon="info-sign")
        ).add_to(m)

    # Snapped – green + connection line
    for s in snapped:
        orig = (s.get('original_lat', s['lat']), s.get('original_lng', s['lng']))
        snap = (s['lat'], s['lng'])
        
        if orig != snap:
            folium.PolyLine([orig, snap], color="gray", weight=1.5, opacity=0.7).add_to(m)
        
        folium.Marker(
            snap,
            popup=f"{s['name']} ({s['roll_no']})<br>Snapped ({s.get('snap_distance_m','?')} m)",
            icon=folium.Icon(color="green", icon="check")
        ).add_to(m)

    m.save("step9_snapping_result.html")
    print(f"Saved: step9_snapping_result.html")
    print(f"Snapped {len(snapped)} / {len(stops)} stops")