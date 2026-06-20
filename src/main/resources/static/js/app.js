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
const documentList = document.getElementById('documentList');
const userName = document.getElementById('userName');
const userAvatar = document.getElementById('userAvatar');
const chatTitle = document.getElementById('chatTitle');
const chatSubtitle = document.getElementById('chatSubtitle');
const conversationList = document.getElementById('conversationList');
const newChatBtn = document.getElementById('newChatBtn');

const user = getUser();
if (user) {
    userName.textContent = user.name || '';
    userAvatar.textContent = user.name ? user.name.charAt(0).toUpperCase() : '';
}

let documents = [];
let conversations = [];
let currentConversationId = null;

loadDocuments();
loadConversations();

function getConversationHistory() {
    const history = [];
    const messageElements = messages.querySelectorAll('.message:not(.welcome)');
    for (const el of messageElements) {
        if (el.classList.contains('streaming')) continue;
        const bubble = el.querySelector('.bubble');
        if (!bubble) continue;
        const text = bubble.textContent.trim();
        if (!text) continue;
        if (el.classList.contains('user')) {
            history.push({ role: 'user', content: text });
        } else if (el.classList.contains('bot')) {
            history.push({ role: 'assistant', content: text });
        }
    }
    return history;
}

// ───── Conversations ─────

async function loadConversations() {
    try {
        const res = await authFetch('/api/conversations');
        if (!res.ok) return;
        conversations = await res.json();
        renderConversations();
    } catch (e) {
    }
}

function renderConversations() {
    conversationList.innerHTML = '';
    if (conversations.length === 0) {
        conversationList.innerHTML = '<p class="empty-state">No conversations yet</p>';
        return;
    }
    conversations.forEach(c => {
        const item = document.createElement('div');
        item.className = 'conversation-item' + (c.id === currentConversationId ? ' active' : '');
        item.dataset.id = c.id;
        item.innerHTML = `
            <span class="conv-icon">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/>
                </svg>
            </span>
            <span class="conv-title">${escapeHtml(c.title)}</span>
            <button class="conv-delete" onclick="deleteConversation('${c.id}')" title="Delete conversation">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18"/>
                    <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
            </button>
        `;
        item.addEventListener('click', () => switchConversation(c.id));
        conversationList.appendChild(item);
    });
}

async function switchConversation(id) {
    if (id === currentConversationId) return;
    saveMessagesToStorage();
    currentConversationId = id;
    loadMessagesFromStorage();
    renderConversations();
    updateChatHeader();
}

async function newConversation() {
    try {
        const res = await authFetch('/api/conversations', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title: 'New Chat' })
        });
        if (!res.ok) return;
        const conv = await res.json();
        saveMessagesToStorage();
        currentConversationId = conv.id;
        clearMessages();
        addWelcomeMessage();
        await loadConversations();
        updateChatHeader();
    } catch (e) {
    }
}

async function deleteConversation(id) {
    if (!confirm('Delete this conversation?')) return;
    try {
        const res = await authFetch('/api/conversations/' + id, { method: 'DELETE' });
        if (!res.ok) return;
        removeMessagesFromStorage(id);
        if (id === currentConversationId) {
            currentConversationId = null;
            clearMessages();
            addWelcomeMessage();
            updateChatHeader();
        }
        await loadConversations();
    } catch (e) {
    }
}

function updateChatHeader() {
    if (currentConversationId) {
        const conv = conversations.find(c => c.id === currentConversationId);
        if (conv) {
            chatTitle.textContent = conv.title;
            chatSubtitle.textContent = new Date(conv.updatedAt).toLocaleDateString();
            return;
        }
    }
    chatTitle.textContent = 'Ask a Question';
    chatSubtitle.textContent = 'Ask questions about your uploaded documents';
}

// ───── Message Storage ─────

const MSG_PREFIX = 'rag_chat_messages_';

function getStorageKey(id) {
    return MSG_PREFIX + id;
}

function saveMessagesToStorage() {
    if (!currentConversationId) return;
    const html = messages.innerHTML;
    try {
        localStorage.setItem(getStorageKey(currentConversationId), html);
    } catch (e) {
    }
}

function loadMessagesFromStorage() {
    if (!currentConversationId) {
        clearMessages();
        addWelcomeMessage();
        return;
    }
    const saved = localStorage.getItem(getStorageKey(currentConversationId));
    clearMessages();
    if (saved) {
        messages.innerHTML = saved;
    } else {
        addWelcomeMessage();
    }
    scrollToBottom();
}

function removeMessagesFromStorage(id) {
    localStorage.removeItem(getStorageKey(id));
}

function clearMessages() {
    messages.innerHTML = '';
}

// ───── Documents ─────

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
    }
}

function enableChat() {
    questionInput.disabled = false;
    sendBtn.disabled = false;
}

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

newChatBtn.addEventListener('click', newConversation);

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
        <span class="doc-name">${escapeHtml(doc.fileName)}</span>
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

    if (!currentConversationId) {
        await newConversation();
    }

    addMessage(question, 'user');
    questionInput.value = '';
    questionInput.disabled = true;
    sendBtn.disabled = true;

    const streamingMsg = addStreamingBotMessage();

    try {
        const res = await authFetch('/ask/stream', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ question, topK: 5, history: getConversationHistory() })
        });

        if (!res.ok) {
            const err = await res.text();
            throw new Error(err || 'Request failed');
        }

        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() || '';
            for (const line of lines) {
                const trimmed = line.trim();
                if (!trimmed || !trimmed.startsWith('data:')) continue;
                const payload = trimmed.startsWith('data: ') ? trimmed.substring(6) : trimmed.substring(5);
                if (payload === '[DONE]') continue;
                try {
                    const parsed = JSON.parse(payload);
                    if (parsed.content != null) {
                        streamingMsg.appendContent(parsed.content);
                    }
                    if (parsed.answer != null) {
                        streamingMsg.appendContent(parsed.answer);
                    }
                    if (parsed.sources) {
                        streamingMsg.setSources(parsed.sources);
                    }
                } catch (e) {
                }
            }
        }
        streamingMsg.finish();

        if (currentConversationId) {
            autoRenameConversation(question);
        }
    } catch (err) {
        streamingMsg.setError(err.message);
    } finally {
        questionInput.disabled = false;
        sendBtn.disabled = false;
        questionInput.focus();
        saveMessagesToStorage();
    }
});

async function autoRenameConversation(firstQuestion) {
    if (!currentConversationId) return;
    const conv = conversations.find(c => c.id === currentConversationId);
    if (!conv || conv.title !== 'New Chat') return;
    const title = firstQuestion.length > 50 ? firstQuestion.substring(0, 50) + '...' : firstQuestion;
    try {
        await authFetch('/api/conversations/' + currentConversationId, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title })
        });
        await loadConversations();
        updateChatHeader();
    } catch (e) {
    }
}

function addWelcomeMessage() {
    const msg = document.createElement('div');
    msg.className = 'message welcome';
    msg.innerHTML = `
        <div class="avatar bot">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2a4 4 0 014 4v2a4 4 0 01-8 0V6a4 4 0 014-4z"/>
                <path d="M20 18v2a4 4 0 01-4 4H8a4 4 0 01-4-4v-2"/>
                <circle cx="12" cy="12" r="3"/>
            </svg>
        </div>
        <div class="bubble">
            <p>Upload a PDF document to get started, then ask me anything about it.</p>
        </div>
    `;
    messages.appendChild(msg);
}

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
                    <strong>${escapeHtml(s.documentName)}</strong> (chunk ${s.chunkIndex})
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

function addStreamingBotMessage() {
    const msg = document.createElement('div');
    msg.className = 'message bot streaming';
    msg.innerHTML = `
        <div class="avatar bot">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2a4 4 0 014 4v2a4 4 0 01-8 0V6a4 4 0 014-4z"/>
                <path d="M20 18v2a4 4 0 01-4 4H8a4 4 0 01-4-4v-2"/>
                <circle cx="12" cy="12" r="3"/>
            </svg>
        </div>
        <div class="bubble">
            <p class="streaming-content"><span class="streaming-cursor">|</span></p>
        </div>
    `;
    const contentP = msg.querySelector('.streaming-content');
    const bubble = msg.querySelector('.bubble');
    messages.appendChild(msg);
    scrollToBottom();

    let sourcesDetails = null;

    return {
        appendContent(text) {
            const cursor = contentP.querySelector('.streaming-cursor');
            if (cursor) {
                cursor.insertAdjacentText('beforebegin', text);
            } else {
                contentP.textContent += text;
            }
            scrollToBottom();
        },
        setSources(sources) {
            if (sourcesDetails) return;
            let html = `<details class="sources">
                <summary>Sources (${sources.length})</summary>
                ${sources.map(s => `
                    <div class="source-item">
                        <strong>${escapeHtml(s.documentName)}</strong> (chunk ${s.chunkIndex})
                        <span class="source-score">relevance: ${(s.score * 100).toFixed(0)}%</span>
                    </div>
                `).join('')}
            </details>`;
            bubble.insertAdjacentHTML('beforeend', html);
            sourcesDetails = bubble.querySelector('details');
            scrollToBottom();
        },
        finish() {
            const cursor = contentP.querySelector('.streaming-cursor');
            if (cursor) cursor.remove();
            msg.classList.remove('streaming');
        },
        setError(msgText) {
            this.finish();
            contentP.textContent = 'Error: ' + msgText;
        }
    };
}

function scrollToBottom() {
    messages.scrollTop = messages.scrollHeight;
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}
