from pymongo import MongoClient
from src.core.config import settings
import logging

logger = logging.getLogger(__name__)

class MongoDB:
    _client = None
    
    @classmethod
    def get_client(cls):
        if cls._client is None:
            try:
                cls._client = MongoClient(settings.MONGODB_URI)
                # Test connection
                cls._client.admin.command('ping')
                logger.info("MongoDB connection successful")
            except Exception as e:
                logger.error(f"MongoDB connection failed: {e}")
                raise
        return cls._client

    @classmethod
    def get_db(cls):
        client = cls.get_client()
        return client[settings.MONGODB_DB_NAME]
