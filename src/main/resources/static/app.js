/* ============================================================
   CBT 最小介入闭环 · 前端
   数据来源：后端唯一端点 POST /api/cbt/turn。
   后端返回 {threadId, node, status, question, userResponse}，
   因此链路能展示「走到哪一步 + 该步问了什么 / 你答了什么」，
   但拿不到 State 明细（证据、替代想法等）—— 需要后端开 /trace。
   见文件末尾 TODO。
   ============================================================ */

'use strict';

/* ---------------- 阶段定义（对应后端 Nodes 常量） ---------------- */

const STAGES = [
  { id: 'safety_triage',             name: '安全评估',     goal: '先判断这一轮是否适合继续自助练习' },
  { id: 'problem_identification',    name: '问题识别',     goal: '把模糊的困扰收敛成一个可以工作的具体问题' },
  { id: 'context_extraction',        name: '情境还原',     goal: '还原当时发生了什么、想到什么、情绪与身体如何反应' },
  { id: 'evidence_review',           name: '证据检验',     goal: '找出支持与反对这个想法的证据，寻找其他解释' },
  { id: 'intervention_decision',     name: '干预选择',     goal: '选一条路：检视想法、做个小实验、解决现实问题、只观察' },
  { id: 'behavioral_experiment',     name: '行为实验',     goal: '把想法当成假设，设计一个可以验证的小行动' },
  { id: 'practical_problem_solving', name: '现实问题解决', goal: '当困难来自客观环境时，拆解可控因素并选一个动作' },
  { id: 'result_evaluation',         name: '结果复盘',     goal: '对照原来的预测和实际发生的结果' },
  { id: 'session_summary',           name: '会话总结',     goal: '整理这次练习的结论与仍未解决的部分' },
  { id: 'persist_record',            name: '记录保存',     goal: '只有你明确同意，才会写入长期记录' }
];

const BRANCHES = [
  { label: '回去继续检验想法',     target: 'evidence_review' },
  { label: '设计一个行为实验',     target: 'behavioral_experiment' },
  { label: '先走一个很小的行动',   target: 'behavioral_experiment' },
  { label: '解决现实中的具体问题', target: 'practical_problem_solving' },
  { label: '这轮只观察，不改动',   target: 'session_summary' },
  { label: '转介专业支持',         target: 'professional_support' }
];

/* ---------------- 状态 ---------------- */

const S = {
  threadId: '',
  turns: [],        // { userText, answeredNode, resp }
  visited: [],
  current: null,
  lastStatus: '',
  busy: false,
  demo: false,
  needDecision: false,
  hasV3Auth: false, // 后端是否已升级到 authState 契约
  openNode: null
};

/* ---------------- DOM ---------------- */

const $ = (id) => document.getElementById(id);
const chainEl = $('chain');
const progressEl = $('progress');
const threadEl = $('thread');
const emptyEl = $('empty');
const inputEl = $('input');
const formEl = $('form');
const sendBtn = $('sendBtn');
const decisionEl = $('decision');
const connEl = $('conn');
const modeTag = $('modeTag');
const stageNameEl = $('stageName');
const stageSubEl = $('stageSub');
const stageDotEl = $('stageDot');
const inputHintEl = $('inputHint');

const esc = (s) => String(s == null ? '' : s)
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

const CHECK = '<svg viewBox="0 0 24 24"><path d="M4 12.5 9.5 18 20 6.5"/></svg>';

const labelOf = (id) => (STAGES.find(s => s.id === id) || {}).name || id;

/* ---------------- 链路状态推导 ---------------- */

function stageStatus(id) {
  const isCurrent = S.current === id;
  if (isCurrent) {
    if (S.busy) return 'active';
    if (S.lastStatus === 'completed') return 'done';
    if (S.needDecision) return 'wait';
    return 'active';
  }
  return S.visited.includes(id) ? 'done' : 'idle';
}

function renderProgress() {
  progressEl.innerHTML = STAGES.map(s => {
    const st = stageStatus(s.id);
    const cls = st === 'done' ? 'is-done' : (st === 'active' || st === 'wait') ? 'is-active' : '';
    return `<span class="${cls}"></span>`;
  }).join('');
}

/** 该阶段最近一次经过对应的那轮 */
function lastTurnOf(nodeId) {
  for (let i = S.turns.length - 1; i >= 0; i--) {
    const t = S.turns[i];
    if (t.resp && t.resp.node === nodeId) return t;
  }
  return null;
}

/** 干预选择实际走了哪条分支：只看 intervention_decision 之后经过的节点 */
function takenFromIntervention(target) {
  const idx = S.visited.indexOf('intervention_decision');
  if (idx < 0) return false;
  return S.visited.indexOf(target, idx + 1) >= 0;
}

function renderChain() {
  const choseBehavior = S.visited.includes('behavioral_experiment');
  const chosePractical = S.visited.includes('practical_problem_solving');
  const rows = [];

  STAGES.forEach((stage, i) => {
    let st = stageStatus(stage.id);

    // 6A / 6B 互斥：走了其中一条，另一条标"本次未走"
    if (st === 'idle') {
      if (stage.id === 'behavioral_experiment' && chosePractical) st = 'skip';
      if (stage.id === 'practical_problem_solving' && choseBehavior) st = 'skip';
    }

    const turn = lastTurnOf(stage.id);
    const q = turn && turn.resp.question ? turn.resp.question : '';
    const a = turn && turn.userText ? turn.userText : '';
    const out = turn && turn.resp.userResponse ? turn.resp.userResponse : '';

    const tagText = { done: '已完成', active: '进行中', wait: '等你回答', idle: '未进入', skip: '本次未走' }[st];

    let body = '';
    if (q) body += `<span class="who">问</span>${esc(q)}`;
    else if (stage.id === 'safety_triage' && st === 'done') {
      body += `<span class="who">状态</span>本轮已通过安全评估，这一步不需要你回答`;
    }
    if (S.openNode === stage.id) {
      if (a) body += `<div style="margin-top:6px"><span class="who">答</span>${esc(a)}</div>`;
      if (out && out !== q) {
        body += `<div style="margin-top:6px"><span class="who">输出</span>${esc(out)}</div>`;
      }
    }

    const pin = st === 'done' ? CHECK : String(i + 1);
    const open = S.openNode === stage.id ? ' is-open' : '';

    rows.push(`
      <li class="node is-${st}${open}" data-id="${stage.id}">
        <span class="pin">${pin}</span>
        <button class="card" type="button">
          <div class="card-top">
            <span class="card-name">${stage.name}</span>
            <span class="card-id">${stage.id}</span>
          </div>
          <div class="card-goal">${stage.goal}</div>
          ${body ? `<div class="card-out">${body}</div>` : ''}
          <span class="card-tag">${tagText}</span>
        </button>
      </li>`);

    if (stage.id === 'intervention_decision') {
      // 后端只回节点名，分不清是 behavioral_experiment 还是 small_behavioral_step，
      // 同目标的多个选项只把第一条标为实际走过的，避免看起来"两条都走了"。
      const seen = new Set();
      const items = BRANCHES.map(b => {
        const reached = takenFromIntervention(b.target);
        const taken = reached && !seen.has(b.target);
        if (reached) seen.add(b.target);
        return `<li class="${taken ? 'is-taken' : ''}"><span class="marker">${taken ? '→' : '·'}</span>${b.label}</li>`;
      }).join('');
      rows.push(`<li class="branch-wrap"><ul class="branch">${items}</ul></li>`);
    }
  });

  chainEl.innerHTML = rows.join('');

  chainEl.querySelectorAll('.node > .card').forEach(card => {
    card.addEventListener('click', () => {
      const id = card.parentElement.dataset.id;
      S.openNode = S.openNode === id ? null : id;
      renderChain();
    });
  });

  // 让当前节点始终在可视区（只在不完全可见时滚，避免抢用户滚动）
  const active = S.current && chainEl.querySelector(`.node[data-id="${S.current}"]`);
  if (active) {
    const box = active.getBoundingClientRect();
    const rail = chainEl.getBoundingClientRect();
    if (box.top < rail.top || box.bottom > rail.bottom) {
      active.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }
  }
}

/* ---------------- 顶部 ---------------- */

function renderStageHead() {
  const cur = STAGES.find(s => s.id === S.current);
  stageNameEl.textContent = cur ? cur.name : '等待开始';
  stageSubEl.textContent = cur ? cur.goal : '说出你最近困扰的一件事';

  stageDotEl.className = 'dot';
  if (S.busy) stageDotEl.classList.add('is-busy');
  else if (S.needDecision) stageDotEl.classList.add('is-wait');
  else if (cur) stageDotEl.classList.add('is-live');

  inputHintEl.textContent = S.busy
    ? '系统正在处理这一步…'
    : S.needDecision
      ? '这一步需要你决定：保存，还是不保存。'
      : 'Enter 发送 · Shift + Enter 换行';
}

function setConn(kind, text) {
  connEl.className = 'conn ' + ({ live: 'is-live', off: 'is-off', down: 'is-down' }[kind] || '');
  connEl.lastElementChild.textContent = text;
}

/* ---------------- 对话 ---------------- */

function renderThread() {
  if (!S.turns.length) {
    threadEl.innerHTML = '';
    emptyEl.hidden = false;
    threadEl.appendChild(emptyEl);
    return;
  }
  emptyEl.hidden = true;

  threadEl.innerHTML = S.turns.map(t => {
    const resp = t.resp;

    const userBlock = t.userText
      ? `<div class="msg msg-user">
           <div class="avatar">你</div>
           <div class="msg-body">
             <div class="msg-meta">${t.answeredNode ? '回应 · ' + esc(labelOf(t.answeredNode)) : '开始'}</div>
             <div class="bubble">${esc(t.userText)}</div>
           </div>
         </div>`
      : '';

    if (!resp) return userBlock;

    const isAlert = resp.status === 'error';
    const isQuiet = resp.status === 'info';

    let main = resp.userResponse || '';
    const q = resp.question || '';
    if (q && main && !main.includes(q)) main += '\n\n' + q;
    if (!main) main = q;

    const agentBlock = `
      <div class="msg msg-agent">
        <div class="avatar">AI</div>
        <div class="msg-body">
          <div class="msg-meta">
            ${resp.node && resp.node !== 'safety_triage' ? '<span class="node-id">安全评估 ✓</span>' : ''}
            ${resp.node ? esc(labelOf(resp.node)) : '提示'}
            ${resp.node ? `<span class="node-id">${esc(resp.node)}</span>` : ''}
          </div>
          <div class="bubble ${isAlert ? 'is-alert' : isQuiet ? 'is-quiet' : ''}">${esc(main)}</div>
        </div>
      </div>`;

    return userBlock + agentBlock;
  }).join('');

  threadEl.scrollTop = threadEl.scrollHeight;
}

function showThinking(on) {
  const existing = document.getElementById('thinking');
  if (!on) { if (existing) existing.remove(); return; }
  if (existing) return;

  const cur = STAGES.find(s => s.id === S.current);
  const div = document.createElement('div');
  div.id = 'thinking';
  div.className = 'thinking';
  div.innerHTML = `
    <div class="avatar">AI</div>
    <div style="display:flex;flex-direction:column;gap:4px">
      <span class="label">${cur ? '正在' + cur.name : '正在处理'}</span>
      <div class="dots"><i></i><i></i><i></i></div>
    </div>`;
  threadEl.appendChild(div);
  threadEl.scrollTop = threadEl.scrollHeight;
}

/* ---------------- 刷新 ---------------- */

function refresh() {
  renderProgress();
  renderChain();
  renderStageHead();
  decisionEl.hidden = !S.needDecision;
}

function setBusy(on) {
  S.busy = on;
  sendBtn.disabled = on;
  inputEl.disabled = on;
  showThinking(on);
  refresh();
}

/* ---------------- 发送 ---------------- */

async function send(text, opts = {}) {
  if (S.busy) return;
  const answer = text == null ? '' : text;
  const answeredNode = S.current;

  if (answer) {
    S.turns.push({ userText: answer, answeredNode, resp: null });
    renderThread();
  }

  setBusy(true);
  inputEl.value = '';
  autoGrow();

  try {
    const body = { answer };
    if (S.threadId) body.threadId = S.threadId;

    // v1 契约用布尔；v3（未实现）用枚举。按后端实际能力选择。
    if (opts.decision === 'APPROVE') {
      if (S.hasV3Auth) body.recordDecision = 'APPROVE';
      else body.recordApproved = true;
    } else if (opts.decision === 'DECLINE' && S.hasV3Auth) {
      body.recordDecision = 'DECLINE';
    }

    const res = await fetch('/api/cbt/turn', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });

    if (!res.ok) throw new Error('HTTP ' + res.status);

    applyResponse(await res.json(), answeredNode);
    setConn('live', '已连接');
  } catch (err) {
    pushInfo('这一步没有走通（' + (err.message || '未知错误') + '）。可以重试，或点右上角「新会话」。');
    setConn('down', '请求失败');
  } finally {
    setBusy(false);
  }
}

function applyResponse(data, answeredNode) {
  if (data.threadId) S.threadId = data.threadId;
  if (typeof data.authState === 'string') S.hasV3Auth = true;

  const last = S.turns[S.turns.length - 1];
  if (last && last.resp === null) {
    last.resp = data;
    last.answeredNode = answeredNode;
  } else {
    S.turns.push({ userText: '', answeredNode, resp: data });
  }

  if (data.node) {
    // 后端只有两条固定边：START -> safety_triage、await_user -> safety_triage，
    // 而响应只回「最后停下的那个节点」。所以这一轮只要落在任何节点上，
    // 安全门必然已经先走过——这里替后端把这一步补回链路里。
    if (data.node !== 'safety_triage' && S.visited.indexOf('safety_triage') < 0) {
      S.visited.push('safety_triage');
    }
    S.visited.push(data.node);
    S.current = data.node;
  }
  S.lastStatus = data.status || '';

  S.needDecision = needsDecision(data);
  renderThread();
  refresh();
}

function needsDecision(resp) {
  if (!resp) return false;
  if (resp.authState === 'PENDING') return true;
  if (resp.status === 'completed') return false;
  if (resp.node !== 'persist_record') return false;
  const text = (resp.question || '') + (resp.userResponse || '');
  return /确认|保存/.test(text) && !/已保存/.test(text);
}

function pushInfo(msg) {
  S.turns.push({
    userText: '', answeredNode: null,
    resp: { node: '', status: 'info', question: '', userResponse: msg }
  });
  S.needDecision = false;
  renderThread();
  refresh();
}

/* ---------------- 输入区 ---------------- */

function autoGrow() {
  inputEl.style.height = 'auto';
  inputEl.style.height = Math.min(inputEl.scrollHeight, 160) + 'px';
}

inputEl.addEventListener('input', autoGrow);

inputEl.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    formEl.requestSubmit();
  }
});

formEl.addEventListener('submit', (e) => {
  e.preventDefault();
  const text = inputEl.value.trim();
  if (!text || S.busy) return;
  send(text);
});

$('saveBtn').addEventListener('click', () => send('确认保存', { decision: 'APPROVE' }));

$('discardBtn').addEventListener('click', () => {
  if (S.hasV3Auth) { send('不用保存', { decision: 'DECLINE' }); return; }
  // v1 后端没有 DECLINE：任何提交都会被再次询问。这里只收起，不假装成功。
  S.needDecision = false;
  pushInfo('好的，这次不做保存。当前后端还不支持「拒绝后不再询问」，所以流程再次走到总结时可能还会问一次；开一个新会话就不会了。');
});

document.querySelectorAll('.chip').forEach(chip => {
  chip.addEventListener('click', () => { if (!S.busy) send(chip.dataset.seed); });
});

$('resetBtn').addEventListener('click', () => {
  clearTimeout(demoTimer);
  S.threadId = ''; S.turns = []; S.visited = []; S.current = null;
  S.lastStatus = ''; S.needDecision = false; S.openNode = null;
  S.demo = false; S.hasV3Auth = false;
  modeTag.hidden = true;
  threadEl.innerHTML = '';
  emptyEl.hidden = false;
  threadEl.appendChild(emptyEl);
  setConn('live', '未发起对话');
  refresh();
});

/* ---------------- 演示模式 ---------------- */

const DEMO_SCRIPT = [
  { user: '最近总是觉得自己做什么都不行', node: 'safety_triage',
    q: '先确认一件重要的事：最近两周里，你有没有出现过想要伤害自己的念头？如果有，我们就先停下练习，一起找能立刻帮到你的人。' },
  { user: '没有，就是很难受', node: 'problem_identification',
    q: '那我们把它说得具体一点：最近一次让你觉得「自己不行」，是什么时候、在做什么事？' },
  { user: '上周小组汇报，我把一个数据讲错了', node: 'context_extraction',
    q: '当时那一瞬间，你脑子里冒出来的第一句话是什么？' },
  { user: '「我完了，他们肯定觉得我很蠢」', node: 'evidence_review',
    q: '有没有哪一点，和他们当时的反应对不上？哪怕很小。' },
  { user: '其实组长后来还说整体讲得挺清楚', node: 'intervention_decision',
    q: '你想先做哪个：继续检验「他们觉得我蠢」这个想法，还是设计一个小行动去验证它？' },
  { user: '想去验证一下', node: 'behavioral_experiment',
    q: '如果「他们觉得我蠢」，下次讨论时他们会怎么做？你打算具体观察什么？' },
  { user: '他们应该不会再问我问题；我看下次还会不会有人来问我数据口径', node: 'result_evaluation',
    q: '实际发生了什么？和你的预测比，差在哪？' },
  { user: '他们照常来问我数据口径，没人提那次错误', node: 'session_summary',
    q: '这一轮整理好了：一次讲错数据，并不等于整体能力被否定。要保存成长期记录吗？' },
  { user: '', node: 'persist_record',
    q: '本轮记录还没有得到你的确认，尚未写入数据库。确认无误后回复「确认保存」。' },
  { user: '确认保存', node: 'persist_record', completed: true,
    q: '已保存。这次你验证的是：一次具体的失误，并不支持「我做什么都不行」这个结论。' }
];

let demoTimer = null;

function startDemo(note) {
  clearTimeout(demoTimer);
  $('resetBtn').click();

  S.demo = true;
  modeTag.hidden = false;
  setConn('off', '演示模式');

  if (note) pushInfo(note);

  let i = 0;
  const step = () => {
    if (i >= DEMO_SCRIPT.length) return;
    const item = DEMO_SCRIPT[i++];
    const answeredNode = S.current;

    if (item.user) {
      S.turns.push({ userText: item.user, answeredNode, resp: null });
      renderThread();
    }

    S.current = item.node;
    S.visited.push(item.node);
    setBusy(true);

    demoTimer = setTimeout(() => {
      const resp = {
        threadId: 'demo-thread',
        node: item.node,
        status: item.completed ? 'completed' : 'waiting_user',
        question: item.q,
        userResponse: item.q
      };
      const last = S.turns[S.turns.length - 1];
      if (last && last.resp === null) last.resp = resp;
      else S.turns.push({ userText: '', answeredNode, resp });

      S.lastStatus = resp.status;
      S.needDecision = needsDecision(resp);
      setBusy(false);
      renderThread();
      refresh();
      demoTimer = setTimeout(step, 850);
    }, 620);
  };

  demoTimer = setTimeout(step, 350);
}

/* ---------------- 启动 ---------------- */

(async function boot() {
  let online = false;
  try {
    // 任何 HTTP 响应都说明服务在（GET 该端点会得到 405），只有网络失败才算离线
    await fetch('/api/cbt/turn', { method: 'GET' });
    online = true;
  } catch { online = false; }

  if (online) {
    setConn('live', '后端已就绪');
  } else {
    setConn('off', '离线演示');
    startDemo('没有连上后端，当前是演示数据。启动后端后刷新页面即为真实对话。');
    return;
  }

  if (location.search.includes('demo=1')) startDemo();
  refresh();
})();

/* TODO（后端缺口，要不要补由你定）：
   GET /api/cbt/threads/{threadId}/trace  →  OverAllState 快照
   补上它，左栏每个节点就能展开真实产出：
   target_problem / automatic_thought / evidence_for / balanced_thought_candidate /
   intervention_type / action_plan / next_step。
   现在这一层只能显示「问」与「答」。 */
