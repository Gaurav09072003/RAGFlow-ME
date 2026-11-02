# RAGFlow — PDF RAG Chatbot

A complete local RAG application:

- Backend: Spring Boot 3.5.16 + Java 17
- Database: H2 file database
- PDF extraction: Apache PDFBox
- Embeddings: Ollama `nomic-embed-text`
- LLM: Ollama `llama3.2:3b`
- Frontend: React + Vite
- Retrieval: cosine similarity calculated in Java
- No external vector database required

## 1. Install Ollama

Install Ollama and make sure it is running.

Pull the two models:

```bash
ollama pull llama3.2:3b
ollama pull nomic-embed-text
```

Check:

```bash
ollama list
```

The backend expects Ollama at:

```text
http://localhost:11434
```

You can change this in:

```text
backend/src/main/resources/application.properties
```

## 2. Start backend

Requirements:

- Java 17+
- Maven 3.9+

Open a terminal in `backend`:

```bash
mvn clean spring-boot:run
```

Backend:

```text
http://localhost:8080
```

Health check:

```text
http://localhost:8080/api/health
```

H2 console:

```text
http://localhost:8080/h2-console
```

H2 JDBC URL:

```text
jdbc:h2:file:./data/ragdb
```

Username:

```text
sa
```

Password:

```text
(empty)
```

## 3. Start frontend

Requirements:

- Node.js 20+

Open another terminal in `frontend`:

```bash
npm install
npm run dev
```

Open:

```text
http://localhost:5173
```

## 4. How RAG works

When you upload a PDF:

1. Spring receives the multipart PDF.
2. PDFBox extracts its text.
3. The text is normalized.
4. The text is split into overlapping chunks.
5. Ollama `nomic-embed-text` creates an embedding for every chunk.
6. H2 stores:
   - PDF filename
   - chunk number
   - chunk text
   - embedding JSON
7. When you ask a question:
   - Ollama embeds the question.
   - Java calculates cosine similarity against all stored chunk vectors.
   - Top matching chunks are selected.
   - Those chunks are sent as context to Ollama.
   - Ollama generates an answer only from that context.
8. The UI displays the retrieved source chunks and similarity scores.

## 5. Important limitation

This version extracts text from normal/text PDFs. Scanned image-only PDFs do not contain machine-readable text and therefore need OCR. The backend reports this clearly instead of silently indexing an empty document.

## 6. Re-upload behavior

Uploading a PDF with the same filename deletes the old chunks and re-indexes the new file. This prevents duplicate chunks from accumulating.

## 7. If Ollama gives a model error

Run:

```bash
ollama pull llama3.2:3b
ollama pull nomic-embed-text
```

Then restart the Spring Boot backend.
