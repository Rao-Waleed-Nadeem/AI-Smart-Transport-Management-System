# scripts/generate_random_students.py
import sys
import os
import random
import time

# Add project root to path
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from firebase_admin import auth, firestore
from database.firebase import get_collection
from config.logging_config import logger

# Collections
users_col     = get_collection('users')      # for Firebase Auth users
students_col  = get_collection('students')   # for student details only

def random_lat_lng_faisalabad():
    lat = random.uniform(31.30, 31.55)
    lng = random.uniform(73.00, 73.20)
    return round(lat, 6), round(lng, 6)

def create_random_student(idx: int):
    lat, lng = random_lat_lng_faisalabad()
    
    # Unique email
    email = f"test.student{idx}@cfd.nu.edu.pk"
    
    # Fixed password as requested
    password = "waleed"
    
    name = f"Test Student {idx}"
    contact = f"03{random.randint(0,9)}{random.randint(10000000,99999999)}"
    
    # Step 2: Create student document in 'students' with doc ID = STUDENT-XXX
    doc_id = f"STUDENT-{idx:03d}"  # STUDENT-001, STUDENT-002, ...

    
    try:
        # Step 1: Create real Firebase Auth user
        user = auth.create_user(
            email=email,
            password=password,
            display_name=name,
            email_verified=False
        )
        uid = user.uid
        
        logger.info(f"Created Auth user: {doc_id} | {email} | password: {password}")

        # Step 2: Store in users collection with exact fields
        user_doc = {
            'name': name,
            'role': 'student',
            'email': email,
            'contact': contact,
            'created_at': firestore.SERVER_TIMESTAMP
        }
        users_col.document(uid).set(user_doc)
        logger.debug(f"Stored user doc for UID {uid}")

       

        student_doc = {
            'name': name,
            'roll_no': f"TEST-{random.randint(1000,9999)}",
            'latitude': lat,
            'longitude': lng,
            'contact': contact,
            'semester': "Spring 2026",
            'fee_status': "unpaid",
            'registration_status': "pending",
            'route_id': None,
            'stop_id': None,
            'created_at': firestore.SERVER_TIMESTAMP,
            'updated_at': firestore.SERVER_TIMESTAMP
        }
        
        students_col.document(uid).set(student_doc)
        logger.info(f"Created student doc {doc_id} at ({lat:.6f}, {lng:.6f})")

    except auth.EmailAlreadyExistsError:
        logger.warning(f"Email {email} already exists — skipping")
    except Exception as e:
        logger.error(f"Failed to create student {idx}: {e}")

# ── Main Execution ──────────────────────────────────────────────────────────

print("Cleaning old test students and users...")
deleted_count = 0

# Delete old test students (roll_no starts with TEST-)
for doc in students_col.where('roll_no', '>=', 'TEST-').stream():
    doc.reference.delete()
    deleted_count += 1

# Delete old test users (name starts with Test Student)
for doc in users_col.where('name', '>=', 'Test Student').stream():
    uid = doc.id  # UID is the document ID
    try:
        auth.delete_user(uid)
    except:
        pass
    doc.reference.delete()
    deleted_count += 1

print(f"Deleted {deleted_count} old test entries.")

print("\nCreating 30 new random students + Auth users...")
for i in range(1, 31):
    create_random_student(i)
    time.sleep(0.3)  # Prevent Firebase Auth rate limiting

print("\nDone! 30 random students created and registered.")
print("→ Document IDs in students: STUDENT-001 to STUDENT-030")
print("→ Users created in Authentication & users collection")
print("Now run your optimization pipeline (/optimize-routes)")