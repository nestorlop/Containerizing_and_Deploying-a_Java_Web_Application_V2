// Web Framework Demo - Client-side JavaScript

async function fetchAndDisplay(url, resultId) {
    const resultEl = document.getElementById(resultId);
    resultEl.textContent = 'Loading...';
    resultEl.className = 'result loading';
    
    try {
        const response = await fetch(url);
        const text = await response.text();
        
        resultEl.textContent = `Status: ${response.status} ${response.statusText}\n` +
                               `Content-Type: ${response.headers.get('Content-Type')}\n` +
                               `Content-Length: ${response.headers.get('Content-Length')}\n\n` +
                               text;
        resultEl.className = response.ok ? 'result success' : 'result error';
    } catch (error) {
        resultEl.textContent = `Error: ${error.message}`;
        resultEl.className = 'result error';
    }
}

function testHello() {
    const name = document.getElementById('helloName').value;
    const url = '/hello' + (name ? '?name=' + encodeURIComponent(name) : '');
    fetchAndDisplay(url, 'helloResult');
}

function testHelloWithLang() {
    const name = document.getElementById('helloName').value;
    const lang = document.getElementById('helloLang').value;
    const params = new URLSearchParams();
    if (name) params.append('name', name);
    if (lang) params.append('language', lang);
    const url = '/hello' + (params.toString() ? '?' + params.toString() : '');
    fetchAndDisplay(url, 'helloResult');
}

function testPi() {
    fetchAndDisplay('/pi', 'piResult');
}

function test404() {
    fetchAndDisplay('/unknown', 'errorResult');
}

// Add some visual feedback styles
const style = document.createElement('style');
style.textContent = `
    .result.loading { color: #ffa500; }
    .result.success { color: #4caf50; }
    .result.error { color: #f44336; }
`;
document.head.appendChild(style);