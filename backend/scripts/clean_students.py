# scripts/clean_students.py
import sys
import os

# Add project root to sys.path (one level up from scripts/)
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))
from database.firebase import get_collection
from firebase_admin import firestore

students_col = get_collection('students')

# Get all students
all_students = students_col.get()

if len(all_students) <= 20:
    print("Already ≤ 20 students — nothing to delete")
else:
    # Sort by created_at (oldest first) or keep newest — change order if needed
    sorted_docs = sorted(all_students, key=lambda doc: doc.to_dict().get('created_at', firestore.SERVER_TIMESTAMP))

    # Keep first 20, delete the rest
    to_delete = sorted_docs[20:]

    for doc in to_delete:
        doc.reference.delete()
        print(f"Deleted student: {doc.id}")

    print(f"Kept {len(all_students) - len(to_delete)} students")