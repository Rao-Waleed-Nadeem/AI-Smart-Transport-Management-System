# data_pipeline/student_loader.py
from database.firebase import get_collection
from config.logging_config import logger
from typing import List, Dict, Any

def load_student_stops() -> List[Dict[str, Any]]:
    """
    Fetches all students from Firestore 'students' collection
    and extracts stop coordinates + basic info.
    
    Returns list of dicts suitable for clustering & snapping.
    """
    try:
        students_ref = get_collection("students")
        students_docs = students_ref.get()  # gets ALL documents

        stops = []
        for doc in students_docs:
            data = doc.to_dict()
            
            # Check required fields (based on your sample document structure)
            if "latitude" in data and "longitude" in data and data["latitude"] and data["longitude"]:
                stop_entry = {
                    "student_id": doc.id,                  # document ID = student_id
                    "name": data.get("name", "Unknown"),
                    "roll_no": data.get("roll_no", ""),
                    "lat": float(data["latitude"]),
                    "lng": float(data["longitude"]),
                    "student_count": 1,                    # each student = 1 "unit" initially
                    "contact": data.get("contact", ""),
                    "semester": data.get("semester", ""),
                    "fee_status": data.get("fee_status", "unknown"),
                    "registration_status": data.get("registration_status", "unknown")
                }
                stops.append(stop_entry)
            else:
                logger.warning(f"Student {doc.id} missing valid lat/lng → skipped")

        if not stops:
            logger.warning("No students with valid stop coordinates found!")
        
        logger.info(f"Loaded {len(stops)} student stops from Firestore")
        logger.debug(f"First 3 stops (sample): {stops[:3]}")
        
        return stops

    except Exception as e:
        logger.error(f"Error loading student stops: {e}", exc_info=True)
        return []