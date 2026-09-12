# DocuMind — RAG Document Q&A System

A portfolio-style **Retrieval-Augmented Generation (RAG)** Document Q&A system built with **Java 17**, **Spring Boot 3.3.5**, **Apache PDFBox**, **FileVectorStore (Java cosine similarity)**, **MongoDB Atlas**, and **OpenRouter API**, hosted on **Render Free**.

Users upload PDFs, the system chunks/embeds them, stores vectors in a file (`/data/vectors.json` on Render, `./data/vectors.json` locally), and answers natural language questions with grounded citations via `openai/gpt-4o-mini`.

This is intentionally a **portfolio/showcase project for interviews**, not a production-scale vector database deployment. The file-based vector store is a deliberate choice for simple, expiry-free hosting on Render Free.

---

## Overview

Users can:

- Sign up / log in (JWT authentication, BCrypt passwords)
- Upload PDF documents (validated, 25MB limit)
- Extract text (PDFBox) → split into chunks (900 chars, 150 overlap) → embed (`text-embedding-3-small`, 384d) → store in `FileVectorStore`
- Ask questions (topK retrieval, min relevance 0.15, max context 7000 chars, last 6 history messages)
- Get grounded answers with source citations (or `I don't know based on the provided documents.`)
- Stream answers via `/ask/stream` (SSE)
- Manage documents and conversations (list/delete/rename)

The system forces the LLM to answer **only** from retrieved context to reduce hallucination.

---

## Architecture Flow

```text
User Browser (static HTML/JS)
        |
        v
Render Free (or localhost:8080)
        |
        v
Spring Boot REST API
        |
        +---- MongoDB Atlas
        |       +-- users
        |       +-- documents
        |       +-- conversations
        |
        +---- FileVectorStore
        |       +-- /data/vectors.json  (Render) / ./data/vectors.json (local)
        |       +-- cosine similarity in Java
        |       +-- userId-filtered search
        |
        +---- OpenRouter
                +-- text embeddings (text-embedding-3-small, 384d)
                +-- GPT-4o-mini (temperature 0.1)
```

**Ingestion:**

```text
PDF → PdfTextExtractor → TextChunker → OpenRouterEmbeddingClient → FileVectorStore → vectors.json
                                                              → StoredDocument (MongoDB)
```

**Query:**

```text
Question → OpenRouterEmbeddingClient → FileVectorStore.search() → cosine similarity → topK → PromptBuilder → OpenRouter GPT-4o-mini → Answer + Sources
```

---

## Tech Stack

| Technology | Purpose | Version/Details |
|---|---|---|
| Java 17 | Backend language | `pom.xml` + `system.properties` |
| Spring Boot 3.3.5 | REST API, Security, Data MongoDB | starter-web, webflux, validation, actuator |
| Spring Security + JJWT 0.12.6 | JWT auth, BCrypt, stateless sessions | `SecurityConfig`, `JwtUtil` |
| Apache PDFBox 3.0.3 | PDF text extraction | `PdfTextExtractor` |
| FileVectorStore | Vector storage, Java cosine similarity | `vector/FileVectorStore.java`, file `vectors.json` |
| MongoDB Atlas | Persistent app data (users, docs, conversations) | `StoredDocument`, `Conversation`, `User` |
| OpenRouter API | Embeddings + LLM (`openai/gpt-4o-mini`) | `OpenRouterChatClient`, `OpenRouterEmbeddingClient` |
| Render Free | Hosting | `render.yaml`, `$PORT`, `VECTOR_FILE_PATH` |
| Maven | Build | `spring-boot-maven-plugin` |

---

## Project Structure

```text
rag-project/
├── render.yaml
├── pom.xml
├── system.properties
├── README.md
├── src/
│   ├── main/
│   │   ├── java/com/example/rag/
│   │   │   ├── api/  DocumentController, ConversationController, DocumentManagementController, dto/*
│   │   │   ├── auth/  AuthController, AuthService, JwtUtil, JwtAuthFilter, User
│   │   │   ├── config/  VectorStoreProperties, EmbeddingProperties, OpenRouterProperties, RagProperties, SecurityConfig, HttpClientConfig
│   │   │   ├── llm/  ChatClient, OpenRouterChatClient
│   │   │   ├── model/  DocumentChunk, SearchResult, StoredDocument, Conversation
│   │   │   ├── service/  DocumentIngestionService, QuestionAnsweringService, TextChunker, PdfTextExtractor, PromptBuilder, OpenRouterEmbeddingClient
│   │   │   ├── vector/  VectorStore (interface), FileVectorStore
│   │   │   └── RagChatbotApplication.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── static/  index.html, login.html, signup.html, css/style.css, js/app.js, js/auth.js
│   └── test/
└── target/
```

---

## RAG Pipeline Explained

1. **PDF Upload** — `POST /upload` validates `.pdf` suffix and non-empty.
2. **Text Extraction** — PDFBox `Loader.loadPDF` + `PDFTextStripper` + normalize.
3. **Chunking** — `TextChunker` splits on `\n\s*\n` paragraphs, packs to `chunk-size 900` with `chunk-overlap 150`.
4. **Embeddings** — Each chunk → `POST https://openrouter.ai/api/v1/embeddings` (`text-embedding-3-small`, 384 dims).
5. **File Storage** — `FileVectorStore` appends `StoredVector{id, userId, documentId, documentName, chunkIndex, text, embedding}` to in-memory list and atomically persists to `vectors.json` (`ReadWriteLock`, temp file + atomic move). Directory created on startup.
6. **Question Embedding** — Same embedding model for the query.
7. **Cosine Search** — Filter by `userId`, compute cosine similarity, sort descending, take `topK` (default 5, max 20), filter by `min-relevance-score 0.15`.
8. **Prompt** — `PromptBuilder` concatenates context up to `max-context-chars 7000` + last 6 history messages, instructs LLM to answer only from context.
9. **LLM** — `POST /chat/completions` to OpenRouter (`gpt-4o-mini`, `temperature 0.1`), non-stream or SSE stream.
10. **Grounded Response** — Returns answer + `SourceChunk[]`; if no relevant chunks → `I don't know based on the provided documents.`

User isolation: a user only retrieves their own vectors (fixed vs the old Qdrant global search).

---

## API Endpoints

All except `/api/auth/**`, static assets, `/actuator/health`, `/swagger-ui/**` require `Authorization: Bearer <JWT>`.

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/signup` | No | `{email, name, password(min6)}` → `{token, email, name}` 201 |
| POST | `/api/auth/login` | No | `{email, password}` → `{token, email, name}` |
| POST | `/upload` | JWT | `multipart file` (PDF) → `{documentId, fileName, chunksStored}` |
| POST | `/upload/multi` | JWT | Multiple PDFs → `List<UploadResponse>` |
| POST | `/ask` | JWT | `{question, topK(1-20), history}` → `{answer, sources}` |
| POST | `/ask/stream` | JWT | Same as `/ask` but `text/event-stream` with `{"content":...}` + `{"sources":...}` + `[DONE]` |
| GET | `/api/documents` | JWT | List own `StoredDocument[]` |
| DELETE | `/api/documents/{documentId}` | JWT | Delete doc + its vectors |
| GET/POST/PATCH/DELETE | `/api/conversations` | JWT | Conversation CRUD (title, updatedAt) |
| GET | `/, /login.html, /signup.html, /css/**, /js/**` | No | Frontend |
| GET | `/actuator/health` | No | `{"status":"UP"}` |
| GET | `/swagger-ui/index.html` | No | OpenAPI docs |

---

## Setup Instructions

### Prerequisites

- Java 17
- Maven 3.9+
- MongoDB Atlas URI
- OpenRouter API key

### 1. Clone

```bash
git clone https://github.com/madaranaruto909-crypto/rag-project.git
cd rag-project
```

### 2. Configure Environment Variables

Create `.env` (gitignored) or export:

```text
MONGO_URI=mongodb+srv://user:pass@cluster0.xxxxx.mongodb.net/quickbite?retryWrites=true&w=majority&appName=Cluster0
OPENROUTER_API_KEY=sk-or-v1-...
JWT_SECRET=your-48-char-secret
VECTOR_FILE_PATH=./data/vectors.json   # local; Render uses /data/vectors.json
```

On Render these are set in Dashboard → Environment.

`application.yml` relevant defaults:

```yaml
vector:
  file-path: ${VECTOR_FILE_PATH:/data/vectors.json}
rag:
  chunk-size: 900
  chunk-overlap: 150
  default-top-k: 5
  max-context-chars: 7000
  min-relevance-score: 0.15
embedding:
  dimension: 384
  model: text-embedding-3-small
openrouter:
  model: openai/gpt-4o-mini
  temperature: 0.1
```

### 3. Run Locally

```bash
mvn spring-boot:run
# or
mvn -DskipTests package && java -jar target/*.jar
```

App starts at `http://localhost:8080` (or `$PORT`). First run creates `./data/vectors.json` automatically.

Swagger: `http://localhost:8080/swagger-ui/index.html`

### 4. Test

Signup → Login → Upload PDF → Ask:

```bash
# Signup
curl -X POST http://localhost:8080/api/auth/signup -H "Content-Type: application/json" -d '{"email":"a@b.com","name":"Alice","password":"secret123"}'
# Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email":"a@b.com","password":"secret123"}' | jq -r .token)
# Upload (Windows)
curl.exe -X POST http://localhost:8080/upload -H "Authorization: Bearer $TOKEN" -F "file=@sample.pdf"
# Ask
curl -X POST http://localhost:8080/ask -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"question":"What is the main topic?","topK":5}'
```

---

## Deploy to Render Free

1. Push to GitHub (`madaranaruto909-crypto/rag-project`).
2. Render Dashboard → **New + → Blueprint** → Connect repo (uses `render.yaml`) **or** **New Web Service → Connect repo** with settings:
   - Build: `mvn -DskipTests package`
   - Start: `java -jar target/*.jar`
   - Environment: `Java 17`
3. Add **Environment Variables**:
   ```
   MONGO_URI=mongodb+srv://...
   OPENROUTER_API_KEY=sk-or-v1-...
   JWT_SECRET=4gk8J2LmP9sX7aR1dN5qT8wY3uH6zC0vB2eF9mK4pL7rS1xD
   VECTOR_FILE_PATH=/data/vectors.json
   ```
   (`PORT` is auto-injected by Render, no need to set)
4. Deploy → check logs for `Loaded 0 vectors from /data/vectors.json` and `Tomcat started on port ...`.
5. Use the generated `https://rag-documind.onrender.com` URL. Generate domain if using Blueprint.

**Note:** Render Free filesystem is **ephemeral**. If the service restarts/redeploys, `/data/vectors.json` is lost and you must re-upload PDFs. MongoDB Atlas data (users, documents, conversations) persists.

---

## Why This Project Matters

- REST + JWT + MongoDB with Spring Boot
- PDF processing + chunking design
- File-based vector store with cosine similarity and user isolation
- RAG prompt engineering and grounded answers
- Streaming SSE
- Clean `VectorStore` abstraction

Suitable for: document search, knowledge base assistants, interview showcase.

---

## Limitations (Intentional for Portfolio)

- File-based vectors, not a production vector DB — all vectors loaded in memory, linear scan.
- No persistent disk on Render Free — vectors lost on restart.
- 384d truncated embeddings (OpenRouter `dimensions` param).
- Single-node, no horizontal scaling for vectors.

For production consider: Qdrant/Weaviate/pgvector, persistent volumes, sharding, reranking, async ingestion.

---

## License

For learning, experimentation, and portfolio use.

## Author

Built as a RAG showcase for interviews — Spring Boot + FileVectorStore + MongoDB Atlas + OpenRouter + Render.
