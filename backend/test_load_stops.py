# test_load_stops.py (temporary - you can delete later)
from data_pipeline.student_loader import load_student_stops
from config.logging_config import logger

stops = load_student_stops()

print(f"\nTotal stops loaded: {len(stops)}")
if stops:
    print("\nSample stops:")
    for s in stops[:5]:
        print(f"  • {s['name']} ({s['roll_no']}) → ({s['lat']:.6f}, {s['lng']:.6f})")
else:
    print("No stops found — check logs/logs/app.log")