import { useEffect, useRef, useState } from "react";
import {
  Bot,
  FileText,
  MessageCircle,
  Paperclip,
  Send,
  Sparkles,
  Trash2,
  UploadCloud,
  User,
  X
} from "lucide-react";

const API = "http://localhost:8080/api";

function App() {
  const [documents, setDocuments] = useState([]);
  const [messages, setMessages] = useState([
    {
      role: "assistant",
      content:
        "Upload a PDF and I’ll index its text into H2. Then ask questions and I’ll retrieve the most relevant chunks before asking Ollama to answer."
    }
  ]);
  const [question, setQuestion] = useState("");
  const [selectedFile, setSelectedFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [asking, setAsking] = useState(false);
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");
  const fileInput = useRef(null);
  const chatEnd = useRef(null);

  useEffect(() => {
    loadDocuments();
  }, []);

  useEffect(() => {
    chatEnd.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, asking]);

  async function loadDocuments() {
    try {
      const response = await fetch(`${API}/documents`);
      if (!response.ok) throw new Error("Backend is not available.");
      setDocuments(await response.json());
    } catch (e) {
      setError(e.message);
    }
  }

  function chooseFile(file) {
    setError("");
    setStatus("");
    if (!file) return;
    if (file.type !== "application/pdf" && !file.name.toLowerCase().endsWith(".pdf")) {
      setError("Please select a PDF file.");
      return;
    }
    setSelectedFile(file);
  }

  async function upload() {
    if (!selectedFile) return;

    setUploading(true);
    setError("");
    setStatus("Extracting PDF text and creating embeddings...");

    const form = new FormData();
    form.append("file", selectedFile);

    try {
      const response = await fetch(`${API}/documents/upload`, {
        method: "POST",
        body: form
      });

      const data = await response.json();
      if (!response.ok) throw new Error(data.error || "Upload failed.");

      await loadDocuments();
      setMessages((old) => [
        ...old,
        {
          role: "assistant",
          content: `Indexed "${data.fileName}" successfully — ${data.pages} page(s), ${data.chunks} semantic chunk(s). You can ask questions about it now.`
        }
      ]);
      setSelectedFile(null);
      setStatus("PDF indexed successfully.");
    } catch (e) {
      setError(e.message);
      setStatus("");
    } finally {
      setUploading(false);
    }
  }

  async function deleteDocument(fileName) {
    try {
      const response = await fetch(
        `${API}/documents?fileName=${encodeURIComponent(fileName)}`,
        { method: "DELETE" }
      );
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || "Delete failed.");
      await loadDocuments();
    } catch (e) {
      setError(e.message);
    }
  }

  async function ask() {
    const text = question.trim();
    if (!text || asking) return;

    if (!documents.length) {
      setError("Upload a PDF before asking a question.");
      return;
    }

    setQuestion("");
    setError("");
    setMessages((old) => [...old, { role: "user", content: text }]);
    setAsking(true);

    try {
      const response = await fetch(`${API}/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ question: text })
      });

      const data = await response.json();
      if (!response.ok) throw new Error(data.error || "Question failed.");

      setMessages((old) => [
        ...old,
        {
          role: "assistant",
          content: data.answer,
          sources: data.sources || []
        }
      ]);
    } catch (e) {
      setError(e.message);
    } finally {
      setAsking(false);
    }
  }

  function onDrop(e) {
    e.preventDefault();
    chooseFile(e.dataTransfer.files?.[0]);
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <div className="brand-icon"><Sparkles size={20} /></div>
          <div>
            <div className="brand-name">RAG<span>Flow</span></div>
            <div className="brand-sub">Local PDF intelligence</div>
          </div>
        </div>
        <div className="stack-status">
          <span className="dot" />
          Ollama + Spring Boot
        </div>
      </header>

      <main className="workspace">
        <aside className="sidebar">
          <section>
            <div className="section-title">
              <span>Knowledge base</span>
              <span className="count">{documents.length}</span>
            </div>

            <div
              className="dropzone"
              onClick={() => fileInput.current?.click()}
              onDragOver={(e) => e.preventDefault()}
              onDrop={onDrop}
            >
              <div className="upload-icon"><UploadCloud size={24} /></div>
              <strong>Drop PDF here</strong>
              <span>or click to browse</span>
              <small>Maximum 50 MB</small>
            </div>

            <input
              ref={fileInput}
              type="file"
              accept="application/pdf,.pdf"
              hidden
              onChange={(e) => chooseFile(e.target.files?.[0])}
            />

            {selectedFile && (
              <div className="selected-file">
                <FileText size={18} />
                <div className="selected-name">
                  <strong>{selectedFile.name}</strong>
                  <span>{formatBytes(selectedFile.size)}</span>
                </div>
                <button className="icon-btn" onClick={() => setSelectedFile(null)}>
                  <X size={16} />
                </button>
              </div>
            )}

            {selectedFile && (
              <button className="primary-btn full" disabled={uploading} onClick={upload}>
                <UploadCloud size={17} />
                {uploading ? "Indexing..." : "Index PDF"}
              </button>
            )}
          </section>

          <section className="documents">
            <div className="section-title">
              <span>Indexed PDFs</span>
            </div>

            {documents.length === 0 ? (
              <div className="empty-docs">
                <FileText size={22} />
                <span>No PDFs indexed yet</span>
              </div>
            ) : (
              <div className="doc-list">
                {documents.map((doc) => (
                  <div className="doc-item" key={doc}>
                    <div className="doc-icon"><FileText size={17} /></div>
                    <div className="doc-name" title={doc}>{doc}</div>
                    <button
                      className="icon-btn danger"
                      title="Delete PDF"
                      onClick={() => deleteDocument(doc)}
                    >
                      <Trash2 size={15} />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </section>

          <div className="sidebar-footer">
            <div className="tech-pill">H2</div>
            <div className="tech-pill">PDFBox</div>
            <div className="tech-pill">Ollama</div>
            <div className="tech-pill">React</div>
          </div>
        </aside>

        <section className="chat-panel">
          <div className="chat-header">
            <div className="chat-title">
              <div className="bot-avatar"><Bot size={21} /></div>
              <div>
                <h1>PDF Assistant</h1>
                <p>Ask questions grounded in your uploaded documents</p>
              </div>
            </div>
            <div className="rag-badge">
              <span className="dot" />
              RAG active
            </div>
          </div>

          <div className="messages">
            <div className="welcome-card">
              <div className="welcome-icon"><MessageCircle size={25} /></div>
              <div>
                <strong>Semantic document search</strong>
                <p>
                  PDFs are extracted, split into overlapping chunks, embedded with
                  Ollama, stored in H2, and ranked by cosine similarity for every question.
                </p>
              </div>
            </div>

            {messages.map((message, index) => (
              <Message key={index} message={message} />
            ))}

            {asking && (
              <div className="message-row assistant">
                <div className="avatar"><Bot size={17} /></div>
                <div className="bubble thinking">
                  <span></span><span></span><span></span>
                </div>
              </div>
            )}
            <div ref={chatEnd} />
          </div>

          <div className="composer-area">
            {error && <div className="error-bar">{error}</div>}
            {status && !error && <div className="status-bar">{status}</div>}

            <div className="composer">
              <button
                className="attach-btn"
                title="Attach PDF"
                onClick={() => fileInput.current?.click()}
              >
                <Paperclip size={19} />
              </button>
              <input
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && !e.shiftKey) {
                    e.preventDefault();
                    ask();
                  }
                }}
                placeholder={documents.length ? "Ask something about your PDF..." : "Upload a PDF to start..."}
                disabled={!documents.length || asking}
              />
              <button
                className="send-btn"
                onClick={ask}
                disabled={!question.trim() || !documents.length || asking}
              >
                <Send size={18} />
              </button>
            </div>
            <div className="composer-hint">Enter to send · Answers are generated from retrieved PDF chunks</div>
          </div>
        </section>
      </main>
    </div>
  );
}

function Message({ message }) {
  const isUser = message.role === "user";

  return (
    <div className={`message-row ${isUser ? "user" : "assistant"}`}>
      <div className="avatar">{isUser ? <User size={17} /> : <Bot size={17} />}</div>
      <div className="message-content">
        <div className="message-label">{isUser ? "You" : "PDF Assistant"}</div>
        <div className="bubble">{message.content}</div>

        {message.sources?.length > 0 && (
          <div className="sources">
            <div className="sources-title">Retrieved sources</div>
            {message.sources.map((source, i) => (
              <div className="source-card" key={`${source.fileName}-${source.chunkIndex}-${i}`}>
                <div className="source-top">
                  <span><FileText size={13} /> {source.fileName}</span>
                  <span>Chunk {source.chunkIndex} · {(source.score * 100).toFixed(1)}%</span>
                </div>
                <p>{source.preview}</p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export default App;
