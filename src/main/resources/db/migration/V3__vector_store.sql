-- Faza 2: baza znanja (RAG). Tablica u obliku koji ocekuje Spring AI PgVectorStore
-- (id uuid, content text, metadata json, embedding vector). Embeddinzi: OpenAI text-embedding-3-small = 1536 dimenzija.

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS vector_store (
    id        UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content   TEXT,
    metadata  JSON,
    embedding VECTOR(1536)
);

CREATE INDEX IF NOT EXISTS vector_store_embedding_idx ON vector_store USING HNSW (embedding vector_cosine_ops);
