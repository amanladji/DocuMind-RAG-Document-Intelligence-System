requireAuth();

const messages = document.getElementById('messages');
const questionForm = document.getElementById('questionForm');
const questionInput = document.getElementById('questionInput');
const sendBtn = document.getElementById('sendBtn');
const fileInput = document.getElementById('fileInput');
const uploadZone = document.getElementById('uploadZone');
const uploadStatus = document.getElementById('uploadStatus');
const statusFileName = document.getElementById('statusFileName');
const statusDetails = document.getElementById('statusDetails');
const uploadProgress = document.getElementById('uploadProgress');
const documentList = document.getElementById('documentList');
const userName = document.getElementById('userName');
const userAvatar = document.getElementById('userAvatar');

const user = getUser();
if (user) {
    userName.textContent = user.name;
    userAvatar.textContent = user.name.charAt(0).toUpperCase();
}

let documents = [];

loadDocuments();

async function loadDocuments() {
    try {
        const res = await authFetch('/api/documents');
        if (!res.ok) return;
        const docs = await res.json();
        documentList.innerHTML = '';
        if (docs.length === 0) {
            documentList.innerHTML = '<p class="empty-state">No documents uploaded</p>';
            documents = [];
            return;
        }
        documents = docs;
        docs.forEach(doc => addDocumentItem(doc));
        enableChat();
    } catch (e) {
        // silently fail
    }
}

function enableChat() {
    questionInput.disabled = false;
    sendBtn.disabled = false;
}

// Upload handlers
uploadZone.addEventListener('click', () => fileInput.click());

uploadZone.addEventListener('dragover', (e) => {
    e.preventDefault();
    uploadZone.classList.add('dragover');
});

uploadZone.addEventListener('dragleave', () => {
    uploadZone.classList.remove('dragover');
});

uploadZone.addEventListener('drop', (e) => {
    e.preventDefault();
    uploadZone.classList.remove('dragover');
    const files = Array.from(e.dataTransfer.files);
    if (files.length > 0) uploadFiles(files);
});

fileInput.addEventListener('change', (e) => {
    const files = Array.from(e.target.files);
    if (files.length > 0) uploadFiles(files);
});

async function uploadFiles(files) {
    const pdfs = Array.from(files).filter(f => f.name.toLowerCase().endsWith('.pdf'));
    if (pdfs.length !== Array.from(files).length) {
        alert('Only PDF files are supported. Non-PDF files were skipped.');
    }
    if (pdfs.length === 0) return;

    uploadStatus.hidden = true;
    const originalText = uploadZone.querySelector('p').textContent;
    let completed = 0;
    let hasError = false;

    for (const file of pdfs) {
        uploadZone.querySelector('p').textContent = `Uploading ${file.name} (${completed + 1}/${pdfs.length})...`;

        const formData = new FormData();
        formData.append('file', file);

        try {
            const res = await authFetch('/upload', {
                method: 'POST',
                body: formData
            });

            if (!res.ok) {
                const err = await res.text();
                console.error(`${file.name}: ${err}`);
                hasError = true;
            } else {
                const data = await res.json();
                completed++;
            }
        } catch (err) {
            console.error(`${file.name}: ${err.message}`);
            hasError = true;
        }
    }

    uploadZone.querySelector('p').textContent = originalText;
    fileInput.value = '';

    if (completed > 0) {
        statusFileName.textContent = `${completed} of ${pdfs.length} uploaded`;
        statusDetails.textContent = `${completed} file${completed !== 1 ? 's' : ''} uploaded successfully`;
        uploadStatus.hidden = false;
        await loadDocuments();
    } else if (hasError) {
        alert('Upload failed. Check the browser console (F12) for details.');
    }
}

function addDocumentItem(doc) {
    const empty = documentList.querySelector('.empty-state');
    if (empty) empty.remove();

    const item = document.createElement('div');
    item.className = 'document-item';
    item.id = 'doc-' + doc.id;
    item.innerHTML = `
        <span class="doc-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
            </svg>
        </span>
        <span class="doc-name">${doc.fileName}</span>
        <span class="doc-chunks">${doc.chunkCount} chunks</span>
        <button class="doc-delete" onclick="deleteDocument('${doc.id}')" title="Delete document">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
            </svg>
        </button>
    `;
    documentList.appendChild(item);
}

async function deleteDocument(id) {
    if (!confirm('Delete this document? All chunks will be removed.')) return;

    const item = document.getElementById('doc-' + id);
    if (item) item.style.opacity = '0.3';

    try {
        const res = await authFetch('/api/documents/' + id, { method: 'DELETE' });
        if (!res.ok) throw new Error('Delete failed');
        documents = documents.filter(d => d.id !== id);
        await loadDocuments();
    } catch (err) {
        alert('Delete failed: ' + err.message);
        if (item) item.style.opacity = '1';
    }
}

// Messaging
questionForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const question = questionInput.value.trim();
    if (!question) return;

    addMessage(question, 'user');
    questionInput.value = '';
    questionInput.disabled = true;
    sendBtn.disabled = true;

    const loadingId = addLoadingMessage();

    try {
        const res = await authFetch('/ask', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ question, topK: 5 })
        });

        removeMessage(loadingId);

        if (!res.ok) {
            const err = await res.text();
            throw new Error(err || 'Request failed');
        }

        const data = await res.json();
        addBotMessage(data.answer, data.sources);
    } catch (err) {
        removeMessage(loadingId);
        addMessage('Error: ' + err.message, 'user');
    } finally {
        questionInput.disabled = false;
        sendBtn.disabled = false;
        questionInput.focus();
    }
});

function addMessage(text, sender) {
    const msg = document.createElement('div');
    msg.className = `message ${sender}`;
    msg.innerHTML = `
        <div class="avatar ${sender}">
            ${sender === 'user'
                ? `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/>
                    <circle cx="12" cy="7" r="4"/>
                   </svg>`
                : `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M12 2a4 4 0 014 4v2a4 4 0 01-8 0V6a4 4 0 014-4z"/>
                    <path d="M20 18v2a4 4 0 01-4 4H8a4 4 0 01-4-4v-2"/>
                    <circle cx="12" cy="12" r="3"/>
                   </svg>`
            }
        </div>
        <div class="bubble"><p>${text}</p></div>
    `;
    messages.appendChild(msg);
    scrollToBottom();
    return msg;
}

function addBotMessage(answer, sources) {
    const msg = document.createElement('div');
    msg.className = 'message bot';

    let sourcesHtml = '';
    if (sources && sources.length > 0) {
        sourcesHtml = `<details class="sources">
            <summary>Sources (${sources.length})</summary>
            ${sources.map(s => `
                <div class="source-item">
                    <strong>${s.documentName}</strong> (chunk ${s.chunkIndex})
                    <span class="source-score">relevance: ${(s.score * 100).toFixed(0)}%</span>
                </div>
            `).join('')}
        </details>`;
    }

    msg.innerHTML = `
        <div class="avatar bot">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2a4 4 0 014 4v2a4 4 0 01-8 0V6a4 4 0 014-4z"/>
                <path d="M20 18v2a4 4 0 01-4 4H8a4 4 0 01-4-4v-2"/>
                <circle cx="12" cy="12" r="3"/>
            </svg>
        </div>
        <div class="bubble">
            <p>${answer}</p>
            ${sourcesHtml}
        </div>
    `;
    messages.appendChild(msg);
    scrollToBottom();
}

function addLoadingMessage() {
    const msg = document.createElement('div');
    msg.className = 'message bot';
    msg.id = 'loading-' + Date.now();
    msg.innerHTML = `
        <div class="avatar bot">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2a4 4 0 014 4v2a4 4 0 01-8 0V6a4 4 0 014-4z"/>
                <path d="M20 18v2a4 4 0 01-4 4H8a4 4 0 01-4-4v-2"/>
                <circle cx="12" cy="12" r="3"/>
            </svg>
        </div>
        <div class="bubble">
            <div class="thinking">
                <span></span><span></span><span></span>
            </div>
        </div>
    `;
    messages.appendChild(msg);
    scrollToBottom();
    return msg.id;
}

function removeMessage(id) {
    const el = document.getElementById(id);
    if (el) el.remove();
}

function scrollToBottom() {
    messages.scrollTop = messages.scrollHeight;
}
