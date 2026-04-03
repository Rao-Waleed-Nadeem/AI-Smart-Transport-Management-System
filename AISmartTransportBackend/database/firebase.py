# database/firebase.py
import firebase_admin
from firebase_admin import credentials, firestore
from config.settings import FIREBASE_CREDENTIALS_PATH
from config.logging_config import logger

# Initialize Firebase only once
if not firebase_admin._apps:
    try:
        cred = credentials.Certificate(FIREBASE_CREDENTIALS_PATH)
        firebase_admin.initialize_app(cred)
        logger.info("Firebase Admin SDK initialized successfully")
    except Exception as e:
        logger.critical(f"Failed to initialize Firebase: {e}")
        raise

db = firestore.client()

def get_collection(collection_name: str):
    """Get a Firestore collection reference"""
    return db.collection(collection_name)

def get_document(collection_name: str, doc_id: str):
    """Get a single document"""
    doc_ref = get_collection(collection_name).document(doc_id)
    doc = doc_ref.get()
    if doc.exists:
        return doc.to_dict()
    return None

def update_document(collection_name: str, doc_id: str, data: dict):
    """Update fields in a document"""
    try:
        get_collection(collection_name).document(doc_id).update(data)
        logger.info(f"Updated document {doc_id} in {collection_name}")
    except Exception as e:
        logger.error(f"Failed to update {collection_name}/{doc_id}: {e}")
        raise