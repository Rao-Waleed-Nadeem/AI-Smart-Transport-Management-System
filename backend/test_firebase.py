# test_firebase.py (temporary)
from database.firebase import get_collection
from config.logging_config import logger

try:
    students = get_collection("students").limit(5).get()
    logger.info(f"Found {len(students)} students")
    for doc in students:
        data = doc.to_dict()
        print(f"Student {doc.id}: {data.get('name')} - lat: {data.get('stop_lat')}")
except Exception as e:
    logger.error(f"Test failed: {e}")