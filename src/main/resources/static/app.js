'use strict';

// 令牌仅保存在当前页面内存中；刷新或关闭页面后需要重新登录。
let accessToken = null;
let currentUser = null;
let isAdmin = false;
let busy = false;
const labels = {dashboard:'工作台', meetings:'会议管理', tasks:'我的行动项', knowledge:'会议知识库', assistant:'AI 助手', admin:'管理后台'};
const el = id => document.getElementById(id);
// 与后端 BusinessStatus 的稳定状态码保持一致。
const STATUS=Object.freeze({DRAFT:1,CONFIRMED:2,RUNNING:3,SUCCEEDED:4,FAILED:5,PROCESSING:6,UNKNOWN:7,SUBMITTING:8,UPLOADED:9,UPLOADING:10,SUBMITTED:11,DELETING:12,DELETED:13});
const STATUS_LABEL=Object.freeze({1:'草稿',2:'已确认',3:'执行中',4:'成功',5:'失败',6:'处理中',7:'状态待确认',8:'提交中',9:'已上传',10:'上传中',11:'已提交',12:'删除中',13:'已删除'});
// 比较正文时统一换行并忽略首尾空白，正文内部空格和段落仍然保留。
const normalizeMeetingText = value => value.replace(/\r\n?/g,'\n').trim();

function showLogin(message = '') {
  accessToken = null;
  currentUser = null;
  isAdmin = false;
  el('app-view').classList.add('hidden');
  el('login-view').classList.remove('hidden');
  el('password').value = '';
  el('login-error').textContent = message;
}

async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
  const response = await fetch(path, {...options, headers, credentials: 'omit'});
  if (response.status === 401 && accessToken) {
    showLogin('登录已失效，请重新登录。');
    throw new Error('登录已失效');
  }
  return response;
}

el('login-form').addEventListener('submit', async event => {
  event.preventDefault();
  const button = el('login-button');
  button.disabled = true;
  el('login-error').textContent = '';
  try {
    const response = await api('/api/auth/login', {method:'POST', headers:{'Content-Type':'application/json'},
      body:JSON.stringify({username:el('username').value.trim(), password:el('password').value})});
    el('password').value = '';
    if (response.status === 401) throw new Error('用户名或密码错误，请重试。');
    if (!response.ok) throw new Error('登录暂不可用，请检查服务是否正常启动。');
    const data = await response.json();
    if (!data.accessToken || !data.username) throw new Error('登录响应不完整，请检查服务配置。');
    accessToken = data.accessToken;
    currentUser = data.username;
    const me = await api('/api/auth/me');
    if (!me.ok) throw new Error('获取用户权限失败，请重试。');
    isAdmin = ((await me.json()).roles || []).includes('ROLE_ADMIN');
    document.querySelector('[data-page="admin"]').classList.toggle('hidden', !isAdmin);
    el('login-view').classList.add('hidden');
    el('app-view').classList.remove('hidden');
    el('sidebar-username').textContent = currentUser;
    const initial = Array.from(currentUser)[0]?.toUpperCase() || 'U';
    el('avatar').textContent = initial;
    el('top-avatar').textContent = initial;
    navigate('dashboard');
  } catch (error) {
    el('password').value = '';
    el('login-error').textContent = error.message || '网络异常，请稍后重试。';
  } finally { button.disabled = false; }
});

el('logout').addEventListener('click', () => showLogin('已退出当前页面。'));
document.querySelectorAll('.nav-item').forEach(button => button.addEventListener('click', () => navigate(button.dataset.page)));

function navigate(page) {
  if (!labels[page]) return;
  el('breadcrumb').textContent = labels[page];
  document.querySelectorAll('.nav-item').forEach(button => button.classList.toggle('active', button.dataset.page === page));
  const content = el('content');
  content.replaceChildren(el(`${page}-template`).content.cloneNode(true));
  content.querySelectorAll('[data-action]').forEach(button => button.addEventListener('click', () => navigate(button.dataset.action)));
  if (page === 'dashboard') content.querySelector('.greeting-name').textContent = currentUser;
  if (page === 'meetings') setupMeetings();
  if (page === 'admin' && isAdmin) setupAdmin();
  if (page === 'assistant') setupChat();
  content.focus({preventScroll:true});
}

async function setupAdmin() {
  const root=el('content');
  root.innerHTML=`<div class="page-heading"><div><div class="section-kicker">ADMINISTRATION</div><h1>用户与角色</h1><p class="subtitle">管理系统账号和系统角色，会议角色在会议内设置。</p></div><div><button class="button outline" id="new-role">＋ 新建角色</button> <button class="button primary" id="new-user">＋ 新建用户</button></div></div><div class="home-grid"><section class="panel"><div class="panel-title"><h3>用户列表</h3><span class="pill">管理员</span></div><div id="admin-users" aria-live="polite">加载中…</div><div class="meeting-pages"><button class="button outline" id="admin-prev">上一页</button><span id="admin-page"></span><button class="button outline" id="admin-next">下一页</button></div></section><section class="panel"><h3>系统角色</h3><p class="tiny-note">角色控制系统权限；会议成员权限单独管理。</p><div id="admin-roles">加载中…</div></section></div><dialog id="admin-dialog"><form id="admin-form"><h2 id="admin-dialog-title"></h2><div id="admin-fields"></div><p id="admin-error" class="error" role="alert"></p><div class="dialog-actions"><button type="button" class="button outline" id="admin-cancel">取消</button><button class="button primary" id="admin-submit">保存</button></div></form></dialog>`;
  const promptButton=document.createElement('button');
  promptButton.className='button outline'; promptButton.textContent='提示词管理';
  promptButton.onclick=()=>setupPrompts();
  el('new-role').before(promptButton,document.createTextNode(' '));
  let page=1, roles=[], submitAction=null;
  const dialog=el('admin-dialog'), error=el('admin-error'), fields=el('admin-fields');
  function open(title,html,action){el('admin-dialog-title').textContent=title;fields.innerHTML=html;error.textContent='';submitAction=action;dialog.showModal();}
  el('admin-cancel').onclick=()=>dialog.close();
  el('admin-form').onsubmit=async e=>{e.preventDefault();const btn=el('admin-submit');btn.disabled=true;error.textContent='';try{await submitAction();dialog.close();await load();}catch(e){error.textContent=e.message||'操作失败。';}finally{btn.disabled=false;}};
  async function request(path,options={}){const r=await api(path,options);if(!r.ok){let detail='';if(r.status===400||r.status===409){try{detail=(await r.json()).message||'';}catch{}}throw new Error(detail||(r.status===403?'没有管理员权限。':r.status===409?'操作冲突，请检查数据。':r.status===400?'输入无效，请检查后重试。':'操作失败，请检查服务状态。'));}return r;}
  const json=(method,body)=>({method,headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
  el('new-user').onclick=()=>open('新建用户','<label class="form-label">用户名<input id="new-username" required maxlength="100" autocomplete="off"></label><label class="form-label">初始密码<input id="new-password" type="password" required minlength="12" autocomplete="new-password"></label><p class="tiny-note">账号创建后可在列表中分配角色。</p>',async()=>{await request('/api/admin/users',json('POST',{username:el('new-username').value.trim(),password:el('new-password').value}));el('new-password').value='';});
  el('new-role').onclick=()=>open('新建系统角色','<label class="form-label">角色编码<input id="new-code" required maxlength="50" placeholder="例如 MANAGER"></label><p class="tiny-note">以大写字母开头，只能输入大写字母、数字和下划线，例如 MANAGER。</p><label class="form-label">角色名称<input id="new-name" required maxlength="100" placeholder="例如 经理"></label><p class="tiny-note">新角色需要后续配置具体权限，创建角色本身不会自动授予业务权限。</p>',async()=>{const code=el('new-code').value.trim();const name=el('new-name').value.trim();if(!/^[A-Z][A-Z0-9_]{0,49}$/.test(code))throw new Error('角色编码须以大写英文字母开头，只能包含大写字母、数字和下划线。');if(!name)throw new Error('请输入角色名称。');await request('/api/admin/roles',json('POST',{code,name}));});
  el('admin-prev').onclick=()=>{if(page>1){page--;load();}};el('admin-next').onclick=()=>{page++;load();};
  async function load(){try{const [usersResponse,rolesResponse]=await Promise.all([request(`/api/admin/users?page=${page}&size=20`),request('/api/admin/roles')]);const users=await usersResponse.json();roles=await rolesResponse.json();if(!document.contains(root)||!el('admin-users'))return;
    const list=el('admin-users');list.replaceChildren();if(!users.length)list.textContent='暂无用户';
    users.forEach(user=>{const row=document.createElement('div');row.className='meeting-row';const info=document.createElement('span');info.className='meeting-row-text';const name=document.createElement('b');name.textContent=user.username;const state=document.createElement('small');state.textContent=user.enabled?'已启用':'已禁用';info.append(name,state);row.append(info);
      const roleButton=document.createElement('button');roleButton.className='button outline';roleButton.textContent='分配角色';roleButton.onclick=async()=>{try{const selected=await (await request(`/api/admin/users/${user.id}/roles`)).json();open(`分配角色：${user.username}`,'<p class="tiny-note">停用角色不能重新分配；如原有停用角色请取消勾选后保存。</p><div id="role-options"></div>',async()=>{const ids=Array.from(fields.querySelectorAll('input:checked')).map(x=>Number(x.value));await request(`/api/admin/users/${user.id}/roles`,json('PUT',{roleIds:ids}));});const box=el('role-options');roles.forEach(role=>{const label=document.createElement('label');label.className='role-option';const checkbox=document.createElement('input');checkbox.type='checkbox';checkbox.value=role.id;checkbox.checked=selected.includes(role.id);checkbox.disabled=!role.enabled&&!checkbox.checked;label.append(checkbox,document.createTextNode(` ${role.name}（${role.code}）${role.enabled?'':' · 已停用'}`));box.append(label);});}catch(e){alert(e.message);}};row.append(roleButton);
      const status=document.createElement('button');status.className='button outline';status.textContent=user.enabled?'禁用':'启用';status.onclick=async()=>{if(!confirm(`确定${user.enabled?'禁用':'启用'}用户 ${user.username}？`))return;try{await request(`/api/admin/users/${user.id}/status`,json('PUT',{enabled:!user.enabled}));await load();}catch(e){alert(e.message);}};row.append(status);list.append(row);});
    const roleList=el('admin-roles');roleList.replaceChildren();roles.forEach(role=>{const row=document.createElement('div');row.className='meeting-row';const label=document.createElement('span');label.className='meeting-row-text';label.textContent=`${role.name}（${role.code}）${role.enabled?'':' · 已停用'}`;row.append(label);const edit=document.createElement('button');edit.className='button outline';edit.textContent='编辑';edit.onclick=()=>{open('编辑系统角色','<label class="form-label">角色名称<input id="edit-role-name" required maxlength="100"></label><label class="role-option"><input id="edit-role-enabled" type="checkbox"> 启用角色</label>',async()=>{await request(`/api/admin/roles/${role.id}`,json('PUT',{name:el('edit-role-name').value.trim(),enabled:el('edit-role-enabled').checked}));});el('edit-role-name').value=role.name;el('edit-role-enabled').checked=role.enabled;};row.append(edit);roleList.append(row);});el('admin-page').textContent=`第 ${page} 页`;el('admin-prev').disabled=page===1;el('admin-next').disabled=users.length<20;
  }catch(e){if(el('admin-users'))el('admin-users').textContent=e.message||'加载失败。';}}
  await load();
}

async function setupMeetings() {
  const root = el('content');
  root.innerHTML = `<div class="page-heading"><div><div class="section-kicker">MEETINGS / 02</div><h1>会议管理</h1><p class="subtitle">创建会议，管理自己的会议记录。</p></div><button class="button primary" id="new-meeting">＋　新建会议</button></div>
    <div class="panel"><div class="panel-title"><div><span class="section-kicker">MEETING LIBRARY</span><h3>我的会议</h3></div><span class="pill">仅显示我创建的会议</span></div><div id="meeting-list" aria-live="polite"><div class="empty">正在加载…</div></div><div class="meeting-pages"><button class="button outline" id="prev-page" disabled>上一页</button><span id="meeting-page">第 1 页</span><button class="button outline" id="next-page" disabled>下一页</button></div></div>
    <dialog id="meeting-dialog"><form id="meeting-form"><h2>新建会议</h2><p class="muted">填写会议基本信息，文字稿将在后续接入。</p><label>会议主题<input id="meeting-title" maxlength="200" required placeholder="例如：设备采购讨论"></label><label>会议时间（可选）<input id="meeting-time" type="datetime-local"></label><p class="tiny-note">会议时间按服务器配置的时区解释。</p><p id="meeting-error" class="error" role="alert"></p><div class="dialog-actions"><button type="button" class="button outline" id="cancel-meeting">取消</button><button type="submit" class="button primary" id="save-meeting">创建会议</button></div></form></dialog>`;
  const dialog = el('meeting-dialog');
  let page = 1;
  el('new-meeting').addEventListener('click', () => { el('meeting-form').reset(); el('meeting-error').textContent=''; dialog.showModal(); });
  el('cancel-meeting').addEventListener('click', () => dialog.close());
  el('prev-page').addEventListener('click', () => { if (page > 1) { page--; load(); } });
  el('next-page').addEventListener('click', () => { page++; load(); });
  async function load() {
    const list = el('meeting-list');
    list.textContent = '正在加载…';
    try {
      const response = await api(`/api/meetings?page=${page}&size=20`);
      if (!response.ok) throw new Error('加载失败，请检查服务与会议表是否已配置。');
      const rows = await response.json();
      if (!document.contains(list)) return;
      list.replaceChildren();
      if (!rows.length) { const empty=document.createElement('div'); empty.className='empty large'; empty.textContent='暂无会议，点击“新建会议”开始。'; list.append(empty); }
      rows.forEach(row => {
        const item=document.createElement('button'); item.className='meeting-row';
        const icon=document.createElement('span'); icon.className='stat-icon mint'; icon.textContent='▤';
        const text=document.createElement('span'); text.className='meeting-row-text';
        const title=document.createElement('b'); title.textContent=row.title;
        const detail=document.createElement('small'); detail.textContent=row.meetingTime ? row.meetingTime.replace('T',' ').slice(0,16) : '会议时间未填写';
        text.append(title,detail); const arrow=document.createElement('span'); arrow.textContent='↗'; item.append(icon,text,arrow);
        item.addEventListener('click', () => openMeeting(row.id));
        list.append(item);
      });
      el('meeting-page').textContent=`第 ${page} 页`; el('prev-page').disabled=page===1; el('next-page').disabled=rows.length<20;
    } catch (error) { if(document.contains(list)) list.textContent=error.message || '加载失败，请稍后重试。'; }
  }
  el('meeting-form').addEventListener('submit', async event => {
    event.preventDefault(); const button=el('save-meeting'); const title=el('meeting-title').value.trim();
    if (!title) { el('meeting-error').textContent='请输入会议主题。'; return; }
    button.disabled=true;
    try {
      const time=el('meeting-time').value;
      const response=await api('/api/meetings',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({title,meetingTime:time ? `${time}:00` : null})});
      if (!response.ok) throw new Error('创建失败，请检查输入和服务状态。');
      dialog.close(); page=1; await load();
    } catch(error) { el('meeting-error').textContent=error.message || '创建失败，请稍后重试。'; }
    finally { button.disabled=false; }
  });
  await load();
}

async function openMeeting(id) {
  const root=el('content');
  try {
    const response=await api(`/api/meetings/${encodeURIComponent(id)}`);
    if (!response.ok) throw new Error('会议不存在或无权访问。');
    const meeting=await response.json();
    root.innerHTML=`<div class="page-heading"><div><button class="text-button" id="back-meetings">← 返回会议列表</button><h1 id="detail-title"></h1><p class="subtitle" id="detail-time"></p></div></div>
      <div class="home-grid"><section class="panel"><div class="panel-title"><div><span class="section-kicker">TRANSCRIPT</span><h3>会议文字稿</h3></div><span class="pill" id="transcript-version">加载中</span></div><p class="subtitle">手工录入会议内容，保存后保留历史版本。</p><textarea id="transcript-content" class="transcript-editor" maxlength="200000" placeholder="请输入会议文字稿"></textarea><p id="transcript-message" class="error" role="status"></p><button class="button primary" id="save-transcript">保存文字稿</button><details id="transcript-source-details" class="speech-unlinked"><summary>查看当前文字稿来源</summary><div id="transcript-source-info" class="tiny-note"></div></details></section>
      <section class="panel"><span class="section-kicker">MEMBERS</span><h3>会议成员</h3><div id="member-list" aria-live="polite"><p class="tiny-note">正在加载成员…</p></div><div id="member-management"><h3>添加会议成员</h3><p class="subtitle">由组织者搜索并选择已有账号。</p><form id="member-form"><label class="form-label">搜索用户名<input id="member-query" maxlength="100" autocomplete="off" placeholder="输入用户名搜索"></label><label class="form-label">选择用户<select id="member-username" required disabled><option value="">请先搜索用户名</option></select></label><p id="member-search-status" class="tiny-note" role="status"></p><label class="form-label">会议角色<select id="member-role"><option value="EDITOR">编辑者</option><option value="VIEWER">查看者</option></select></label><p class="tiny-note">查看者可查看文字稿，但不能修改。</p><p id="member-message" class="error" role="status"></p><button class="button outline">添加成员</button></form></div></section></div><section class="panel" id="outline-panel"><div class="panel-title"><div><span class="section-kicker">OUTLINE</span><h3>会议大纲</h3></div><span class="pill" id="outline-status">加载中</span></div><p class="subtitle">根据已保存文字稿生成草稿，修改并确认后用于后续纪要。</p><div id="outline-items"></div><p id="outline-message" class="error" role="status"></p><div class="dialog-actions"><button class="button outline" id="outline-generate">AI 生成草稿</button><button class="button outline" id="outline-add">添加条目</button><button class="button outline" id="outline-save">保存草稿</button><button class="button primary" id="outline-confirm">确认大纲</button></div></section>`;
    // 移动现有节点而非重新渲染，切换标签时保留尚未保存的输入。
    const grid=root.querySelector('.home-grid');
    const [transcriptPanel,memberPanel]=grid.querySelectorAll(':scope > section');
    const outlinePanel=el('outline-panel');
    const minutesPanel=document.createElement('section'); minutesPanel.className='panel'; minutesPanel.id='minutes-panel';
    minutesPanel.innerHTML=`<div class="panel-title"><div><span class="section-kicker">MINUTES</span><h3>会议纪要</h3></div><span class="pill" id="minutes-status">加载中</span></div><p class="subtitle">根据已确认的最新大纲与文字稿生成草稿，人工修改后由组织者确认。</p><textarea id="minutes-content" class="transcript-editor" maxlength="20000" placeholder="生成后可在此编辑会议纪要"></textarea><p id="minutes-message" class="error" role="status"></p><div class="dialog-actions"><button class="button outline" id="minutes-generate">AI 生成草稿</button><button class="button outline" id="minutes-save">保存草稿</button><button class="button primary" id="minutes-confirm">确认纪要</button></div><p class="tiny-note">行动项提取与 OA 同步将在后续开发。</p>`;
    const tablist=document.createElement('div'); tablist.className='meeting-tabs'; tablist.setAttribute('role','tablist'); tablist.setAttribute('aria-label','会议详情');
    const paneContainer=document.createElement('div'); paneContainer.className='meeting-tab-content';
    const steps=[
      {key:'transcript',label:'文字稿',number:'01',node:transcriptPanel},
      {key:'outline',label:'会议大纲',number:'02',node:outlinePanel},
      {key:'minutes',label:'纪要与行动项',number:'03',node:minutesPanel},
      {key:'oa',label:'OA 同步',number:'04',placeholder:'OA 同步功能尚未接入，后续将在这里确认并跟进任务。'},
      {key:'members',label:'成员与权限',number:'05',node:memberPanel}
    ];
    grid.before(tablist,paneContainer);
    for (const [index,step] of steps.entries()) {
      const button=document.createElement('button'); button.type='button'; button.className='meeting-tab'; button.id=`meeting-tab-${step.key}`; button.setAttribute('role','tab'); button.setAttribute('aria-controls',`meeting-pane-${step.key}`);
      const number=document.createElement('span'); number.className='meeting-tab-number'; number.textContent=step.number; button.append(number,document.createTextNode(step.label));
      const pane=document.createElement('div'); pane.id=`meeting-pane-${step.key}`; pane.setAttribute('role','tabpanel'); pane.setAttribute('aria-labelledby',button.id); pane.tabIndex=0;
      if(step.node) pane.append(step.node); else {const empty=document.createElement('section');empty.className='panel meeting-placeholder';const heading=document.createElement('h3');heading.textContent=step.label;const description=document.createElement('p');description.className='subtitle';description.textContent=step.placeholder;empty.append(heading,description);pane.append(empty);}
      tablist.append(button); paneContainer.append(pane);
      button.addEventListener('click',()=>activateMeetingTab(index));
      button.addEventListener('keydown',event=>{let target;if(event.key==='ArrowRight')target=(index+1)%steps.length;else if(event.key==='ArrowLeft')target=(index+steps.length-1)%steps.length;else if(event.key==='Home')target=0;else if(event.key==='End')target=steps.length-1;else return;event.preventDefault();activateMeetingTab(target);tablist.children[target].focus();});
    }
    function activateMeetingTab(index){Array.from(tablist.children).forEach((button,i)=>{const active=i===index;button.classList.toggle('active',active);button.setAttribute('aria-selected',String(active));button.tabIndex=active?0:-1;paneContainer.children[i].hidden=!active;});}
    grid.remove(); activateMeetingTab(0);
    el('detail-title').textContent=meeting.title;
    el('detail-time').textContent=meeting.meetingTime ? meeting.meetingTime.replace('T',' ').slice(0,16) : '会议时间未填写';
    const memberList = el('member-list');
    async function loadMembers() {
      try {
        const response = await api(`/api/meetings/${encodeURIComponent(id)}/members`);
        if (!response.ok) throw new Error('成员列表加载失败，请稍后重试。');
        const members = await response.json();
        if (!document.contains(memberList)) return;
        memberList.replaceChildren();
        const labels = {ORGANIZER:'组织者', EDITOR:'编辑者', VIEWER:'查看者'};
        const isOrganizer = members.some(member => member.username === currentUser && member.memberRole === 'ORGANIZER');
        members.forEach(member => {
          const row=document.createElement('div'); row.className='meeting-row';
          const name=document.createElement('span'); name.className='meeting-row-text'; name.textContent=member.username;
          const role=document.createElement('span'); role.className='pill'; role.textContent=labels[member.memberRole] || '未知角色';
          row.append(name, role);
          if (!member.enabled) { const status=document.createElement('small'); status.className='tiny-note'; status.textContent='账号已禁用'; row.append(status); }
          if (isOrganizer && member.memberRole !== 'ORGANIZER') {
            const change=document.createElement('button'); change.type='button'; change.className='button outline'; change.textContent='切换角色';
            change.addEventListener('click', async () => {
              const next=member.memberRole==='EDITOR'?'VIEWER':'EDITOR';
              if (!confirm(`将 ${member.username} 的会议角色改为${labels[next]}？`)) return;
              change.disabled=true;
              try {
                const response=await api(`/api/meetings/${encodeURIComponent(id)}/members/${encodeURIComponent(member.username)}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({expectedRole:member.memberRole,newRole:next})});
                if(response.status===409) throw new Error('成员信息已变化，请重新操作。');
                if(!response.ok) throw new Error('修改失败，请检查权限并重试。');
              } catch(error) { alert(error.message || '修改失败。'); } finally { await loadMembers(); }
            });
            const remove=document.createElement('button'); remove.type='button'; remove.className='button outline'; remove.textContent='移除';
            remove.addEventListener('click', async () => {
              if (!confirm(`移除 ${member.username} 后，该成员将无法再访问会议及文字稿。确定移除？`)) return;
              remove.disabled=true;
              try {
                const response=await api(`/api/meetings/${encodeURIComponent(id)}/members/${encodeURIComponent(member.username)}?expectedRole=${encodeURIComponent(member.memberRole)}`,{method:'DELETE'});
                if(response.status===409) throw new Error('成员信息已变化，请重新操作。');
                if(!response.ok) throw new Error('移除失败，请检查权限并重试。');
              } catch(error) { alert(error.message || '移除失败。'); } finally { await loadMembers(); }
            });
            row.append(change, remove);
          }
          memberList.append(row);
        });
        return members.find(member => member.username === currentUser)?.memberRole || null;
      } catch (error) { if (document.contains(memberList)) memberList.textContent=error.message || '成员列表加载失败。'; return null; }
    }
    const currentRole = await loadMembers();
    if (!document.contains(root) || !el('transcript-content')) return;
    if (currentRole !== 'ORGANIZER') el('member-management').classList.add('hidden');
    let version=0, saved=''; const editor=el('transcript-content');
    el('back-meetings').addEventListener('click',()=>{const minutesEditor=el('minutes-content');const minutesDirty=minutesEditor && minutesEditor.value!==(minutesEditor.dataset.saved||'');if((editor.value!==saved || minutesDirty) && !confirm('文字稿或纪要有未保存的内容，确定返回吗？')) return; navigate('meetings');});
    const draft=await api(`/api/meetings/${encodeURIComponent(id)}/transcript`);
    if (draft.ok) { const data=await draft.json(); version=data.version; saved=data.content; editor.value=saved; el('transcript-version').textContent=`版本 ${version}`; }
    else if (draft.status===404) { el('transcript-version').textContent='暂无文字稿'; }
    else { editor.disabled=true; el('save-transcript').disabled=true; el('transcript-message').textContent='加载文字稿失败，请稍后重试。'; }
    if (currentRole !== 'ORGANIZER' && currentRole !== 'EDITOR') {
      editor.readOnly = true;
      el('save-transcript').disabled = true;
      el('transcript-message').textContent = currentRole === 'VIEWER' ? '当前为只读权限。' : '无法确认权限，请重新打开会议。';
    }
    if (currentRole === 'ORGANIZER' || currentRole === 'EDITOR') { setupOutline(id, currentRole, () => version, () => editor.value !== saved); setupMinutes(id, currentRole, () => editor.value !== saved); }
    if (currentRole === 'ORGANIZER' || currentRole === 'EDITOR') setupTranscription(id,editor,()=>version);
    else { const panel=el('outline-panel'); panel.replaceChildren(); const text=document.createElement('p'); text.className='subtitle'; text.textContent='当前角色暂不能查看会议大纲。'; panel.append(text); }
    if (currentRole !== 'ORGANIZER' && currentRole !== 'EDITOR') { minutesPanel.replaceChildren(); const text=document.createElement('p'); text.className='subtitle'; text.textContent='当前角色暂不能查看会议纪要。'; minutesPanel.append(text); }
    el('transcript-source-details').addEventListener('toggle',async event=>{if(!event.currentTarget.open)return;const info=el('transcript-source-info');if(!version){info.textContent='暂无已保存文字稿。';return;}info.textContent='正在加载来源…';try{const r=await api(`/api/meetings/${encodeURIComponent(id)}/transcript/source?version=${version}`);if(!r.ok)throw new Error('来源加载失败。');const data=await r.json();info.replaceChildren();if(!data.draftId){info.textContent='该版本没有关联 AI 整理草稿。';return;}const meta=document.createElement('p');meta.textContent=`草稿 ${data.draftId} · AI 调用 ${data.aiCallId}`;info.append(meta);for(const [index,source] of data.sources.entries()){const line=document.createElement('p');line.textContent=`${index+1}. 录音 ${source.recordingId||'未关联'} · 转写任务 ${source.taskId} · 说话人校对版本 ${source.speakerReviewVersion}`;info.append(line);}}catch(e){info.textContent=e.message||'来源加载失败。';}});
    el('save-transcript').addEventListener('click',async()=>{
      const button=el('save-transcript'); button.disabled=true; el('transcript-message').textContent='';
      if(version>0 && !editor.dataset.aiDraftId && normalizeMeetingText(editor.value)===normalizeMeetingText(saved)){el('transcript-message').textContent='文字稿内容未变化，无需保存。';button.disabled=false;return;}
      try { const r=await api(`/api/meetings/${encodeURIComponent(id)}/transcript`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({content:editor.value,expectedVersion:version,aiDraftId:editor.dataset.aiDraftId||null})});
        if(r.status===409) throw new Error('文字稿已被更新，请复制当前内容并重新打开会议。');
        if(r.status===404) throw new Error('会议不存在或你没有文字稿编辑权限。');
        if(!r.ok) throw new Error('保存失败，请检查文字稿内容。');
        const data=await r.json(); const unchanged=data.version===version; version=data.version; saved=data.content; delete editor.dataset.aiDraftId; el('transcript-source-details').open=false; el('transcript-version').textContent=`版本 ${version}`; el('transcript-message').textContent=unchanged?'文字稿内容未变化，无需保存。':'保存成功。';
      } catch(error) { el('transcript-message').textContent=error.message || '保存失败。'; } finally { button.disabled=false; }
    });
    const memberQuery=el('member-query'), memberSelect=el('member-username'), searchStatus=el('member-search-status');
    let searchTimer, searchSequence=0;
    memberQuery.addEventListener('input',()=>{
      clearTimeout(searchTimer); const sequence=++searchSequence; const query=memberQuery.value.trim();
      memberSelect.replaceChildren(new Option(query ? '正在搜索…' : '请先搜索用户名', '')); memberSelect.disabled=true;
      searchStatus.textContent=''; if (!query) return;
      searchTimer=setTimeout(async()=>{
        try {
          const r=await api(`/api/meetings/${encodeURIComponent(id)}/eligible-users?query=${encodeURIComponent(query)}`);
          if(sequence!==searchSequence || !document.contains(memberSelect)) return;
          if(r.status===404) throw new Error('只有会议组织者可以搜索用户。');
          if(!r.ok) throw new Error('搜索失败，请稍后重试。');
          const names=await r.json();
          if(sequence!==searchSequence || !document.contains(memberSelect)) return;
          memberSelect.replaceChildren(new Option(names.length ? '请选择用户' : '没有匹配的可添加用户', ''));
          names.forEach(name=>memberSelect.add(new Option(name,name)));
          memberSelect.disabled=names.length===0;
          searchStatus.textContent=names.length===20 ? '最多显示 20 个结果，请输入更完整的用户名。' : '';
        } catch(error) { if(sequence===searchSequence && document.contains(searchStatus)) {memberSelect.replaceChildren(new Option('无法搜索用户','')); searchStatus.textContent=error.message || '搜索失败。';} }
      },300);
    });
    el('member-form').addEventListener('submit',async event=>{
      event.preventDefault(); const message=el('member-message'); message.textContent=''; const button=event.currentTarget.querySelector('button'); button.disabled=true;
      if (!memberSelect.value) { message.textContent='请先搜索并选择用户。'; button.disabled=false; return; }
      try { const r=await api(`/api/meetings/${encodeURIComponent(id)}/members`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:el('member-username').value.trim(),role:el('member-role').value})});
        if(r.status===409) throw new Error('该成员已存在。'); if(r.status===404) throw new Error('只有会议组织者可以添加成员。');
        if(!r.ok) throw new Error('添加失败，请确认用户存在且已启用。'); message.textContent='成员已添加。'; memberQuery.value=''; memberSelect.replaceChildren(new Option('请先搜索用户名','')); memberSelect.disabled=true; await loadMembers();
      } catch(error) { message.textContent=error.message || '添加失败。'; } finally { button.disabled=false; }
    });
  } catch(error) { alert(error.message || '会议加载失败。'); }
}

async function setupTranscription(id, editor, transcriptVersion) {
  const box = document.createElement('section');
  box.className = 'transcription-box';
  box.innerHTML = `<h3>录音文件转写</h3><p class="tiny-note">上传 WAV、MP3 或 OGG 录音（最大 100 MB）；说话人编号需要人工核对。</p>
    <label class="form-label">选择录音<input id="speech-file" type="file" accept=".wav,.mp3,.ogg,audio/wav,audio/mpeg,audio/ogg"></label>
    <button type="button" class="button primary" id="speech-upload">上传录音</button>
    <section class="speech-history"><div class="speech-history-heading"><h4>会议录音</h4><button type="button" class="button outline" id="recordings-refresh">刷新状态</button></div>
    <p class="tiny-note">勾选多条已成功转写的录音，按勾选顺序合并为文字稿草稿。</p><button type="button" class="button outline" id="speech-assemble">合并选中结果</button> <button type="button" class="button primary" id="speech-organize">AI 整理选中结果</button> <span class="tiny-note" id="speech-selected-count">已选择 0 条</span><div id="recordings-list" role="list" aria-label="会议录音列表"></div><details class="speech-unlinked"><summary>未关联录音的历史任务</summary><div id="unlinked-history"></div></details><div class="speech-pages"><button type="button" class="button outline" id="recordings-prev">上一页</button><span id="recordings-page"></span><button type="button" class="button outline" id="recordings-next">下一页</button></div></section>
    <p id="speech-message" class="tiny-note" role="status"></p><section id="speech-preview" hidden><h4 id="speech-preview-title">转写结果预览</h4><textarea id="speech-result" class="transcript-editor" readonly aria-label="转写结果预览"></textarea><div id="speaker-review"></div><details id="chunk-preview"><summary>预览技术分段</summary><p class="tiny-note">仅按长度与发言边界切分，不调用 AI；上文仅供参考。</p><button type="button" class="button outline" id="chunk-preview-load">加载分段</button><div id="chunk-preview-list"></div></details><div class="dialog-actions"><button type="button" class="button outline" id="speech-import">导入文字稿</button></div></section>`;
  editor.closest('.panel').append(box);
  const list=el('recordings-list'), message=el('speech-message'), preview=el('speech-preview'), result=el('speech-result');
  let page=0, requestSeq=0, previewSeq=0, selectedTask=null, historyOpen=null, refreshTimer=null, review=null, reviewMembers=[], reviewDirty=false, reviewPage=0;
  const busy=new Set(), selectedSources=new Map(), pendingExtractions=new Map(), pageSize=20;let pendingOrganize=null;
  const taskLabels={ [STATUS.SUBMITTING]:'提交中', [STATUS.PROCESSING]:'识别中', [STATUS.UNKNOWN]:'状态待确认', [STATUS.SUCCEEDED]:'识别成功', [STATUS.FAILED]:'识别失败' };
  const active = s => [STATUS.SUBMITTING, STATUS.PROCESSING, STATUS.UNKNOWN].includes(s);
  const url = suffix => `/api/meetings/${encodeURIComponent(id)}/transcriptions${suffix}`;
  const button=(text, handler, disabled=false)=>{const b=document.createElement('button');b.type='button';b.className='button outline';b.textContent=text;b.disabled=disabled;b.onclick=handler;return b;};
  function taskStatus(item){
    if(item.status===STATUS.DELETED)return '录音已删除';
    if(item.status===STATUS.DELETING)return '删除中';
    if(item.status===STATUS.SUBMITTING)return '提交中';
    if(!item.transcriptionTaskId)return '待转写';
    return taskLabels[item.transcriptionStatus] || '任务状态未知';
  }
  function displaySpeaker(segment,index){const a=review?.assignments[index];return a?.memberUsername||a?.externalName||(segment.speakerLabel==='unknown'?'未知说话人':`说话人 ${segment.speakerLabel}`);}
  function refreshPreview(){result.value=selectedTask.segments.map((s,i)=>`${displaySpeaker(s,i)}：${s.text}`).join('\n');el('speech-import').disabled=reviewDirty||!selectedTask.segments.length;}
  function personControls(initial, onChange){
    const wrap=document.createElement('div');wrap.className='speaker-controls';const select=document.createElement('select'),input=document.createElement('input');select.setAttribute('aria-label','选择会议成员或外部人员');select.append(new Option('未确认',''));for(const member of reviewMembers)select.append(new Option(member.username,`member:${member.username}`));select.append(new Option('其他人员（手工填写）','__external__'));input.placeholder='输入外部人员姓名';input.maxLength=100;input.setAttribute('aria-label','外部人员姓名');
    if(initial?.memberUsername&&!reviewMembers.some(m=>m.username===initial.memberUsername))select.add(new Option(`${initial.memberUsername}（已不是会议成员）`,`member:${initial.memberUsername}`));
    select.value=initial?.externalName?'__external__':(initial?.memberUsername?`member:${initial.memberUsername}`:'');input.value=initial?.externalName||'';input.hidden=select.value!=='__external__';
    function changed(){input.hidden=select.value!=='__external__';onChange({memberUsername:select.value.startsWith('member:')?select.value.slice(7):null,externalName:select.value==='__external__'?(input.value.trim()||null):null});}
    select.onchange=changed;input.oninput=changed;wrap.append(select,input);return wrap;
  }
  function renderReview(){
    const container=el('speaker-review');container.replaceChildren();const title=document.createElement('h4');title.textContent='说话人校对';const note=document.createElement('p');note.className='tiny-note';note.textContent='可先按识别编号批量绑定会议成员，再逐句修正；不同转写任务的编号互不相通。';container.append(title,note);
    const labels=[...new Set(selectedTask.segments.map(s=>s.speakerLabel))].filter(x=>x!=='unknown');
    for(const label of labels){const line=document.createElement('div');line.className='speaker-review-row';const name=document.createElement('span');name.textContent=`说话人 ${label} 批量设置为`;let chosen={memberUsername:null,externalName:null};const picker=personControls(null,a=>{chosen=a;});const apply=button('应用',()=>{for(let i=0;i<selectedTask.segments.length;i++)if(selectedTask.segments[i].speakerLabel===label)review.assignments[i]={...chosen};reviewDirty=true;refreshPreview();renderReview();});line.append(name,picker,apply);container.append(line);}
    const head=document.createElement('p');head.className='tiny-note';head.textContent='逐句修正（未知说话人请逐句确认）';container.append(head);
    const size=30,start=reviewPage*size;for(let i=start;i<Math.min(start+size,selectedTask.segments.length);i++){const segment=selectedTask.segments[i],row=document.createElement('div');row.className='speaker-review-row';const text=document.createElement('span');text.textContent=`${i+1}. [${segment.speakerLabel==='unknown'?'未知':segment.speakerLabel}] ${segment.text}`;row.append(text,personControls(review.assignments[i],a=>{review.assignments[i]=a;reviewDirty=true;refreshPreview();}));container.append(row);}
    const pages=document.createElement('div');pages.className='speech-pages';pages.append(button('上一页',()=>{reviewPage--;renderReview();},reviewPage===0));const number=document.createElement('span');number.textContent=`第 ${reviewPage+1} 页`;pages.append(number,button('下一页',()=>{reviewPage++;renderReview();},start+size>=selectedTask.segments.length));container.append(pages);
    const save=button('保存说话人校对',async()=>{const savingTask=selectedTask.id,savingSeq=previewSeq;save.disabled=true;container.inert=true;try{const r=await api(url(`/${encodeURIComponent(savingTask)}/speaker-review`),{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(review)});if(r.status===409)throw new Error('校对内容已被其他人修改，请重新打开转写结果。');if(r.status===400)throw new Error('说话人信息无效，请检查会议成员和外部姓名。');if(!r.ok)throw new Error('保存校对失败，请稍后重试。');const saved=await r.json();if(savingSeq!==previewSeq||selectedTask?.id!==savingTask)return;review=saved;reviewDirty=false;refreshPreview();message.textContent='说话人校对已保存。';}catch(e){if(savingSeq===previewSeq)message.textContent=e.message||'保存失败。';}finally{save.disabled=false;container.inert=false;}});container.append(save);
  }
  async function showTask(taskId, filename){
    if(reviewDirty&&!confirm('当前说话人校对尚未保存，切换后会丢失修改，确定继续吗？'))return;
    const seq=++previewSeq;message.textContent='正在加载转写结果…';
    try{const [r,people,reviewResponse]=await Promise.all([api(url(`/${encodeURIComponent(taskId)}`)),api(`/api/meetings/${encodeURIComponent(id)}/members`),api(url(`/${encodeURIComponent(taskId)}/speaker-review`))]);if(!r.ok||!people.ok||!reviewResponse.ok)throw new Error('读取转写结果或说话人校对失败，请重试。');const [data,members,reviewData]=await Promise.all([r.json(),people.json(),reviewResponse.json()]);if(seq!==previewSeq||!document.contains(box))return;
      if(data.status!==STATUS.SUCCEEDED){message.textContent=`${filename}：${taskLabels[data.status]||'状态未知'}`;return;}
      selectedTask=data;review=reviewData;reviewMembers=members;reviewDirty=false;reviewPage=0;el('chunk-preview').open=false;el('chunk-preview-list').replaceChildren();preview.hidden=false;el('speech-preview-title').textContent=`转写结果预览：${filename}`;
      refreshPreview();renderReview();message.textContent=data.segments.length?'请核对说话人和内容。':'未识别到人声或文本。';
    }catch(e){if(seq===previewSeq&&document.contains(box))message.textContent=e.message||'读取转写结果失败。';}
  }
  async function mutate(item, action){
    if(busy.has(item.id))return;
    if(action==='submit' && item.transcriptionTaskId && !confirm('重新转写会创建新任务并可能产生费用，确定继续吗？'))return;
    if(action==='delete' && !confirm('确定删除该录音文件吗？已生成的转写和会议内容将保留，录音删除后不能恢复。'))return;
    busy.add(item.id);message.textContent=action==='delete'?'正在删除录音…':action==='query'?'正在查询结果…':'正在提交转写…';await loadRecordings(page);
    try{
      let r;
      if(action==='submit')r=await api(url(`/recordings/${encodeURIComponent(item.id)}/submit`),{method:'POST'});
      if(action==='delete')r=await api(url(`/recordings/${encodeURIComponent(item.id)}`),{method:'DELETE'});
      if(action==='query')r=await api(url(`/${encodeURIComponent(item.transcriptionTaskId)}/query`),{method:'POST'});
      if(r.status===409)throw new Error('任务正在处理或状态待确认，请稍后查询。');
      if(!r.ok)throw new Error(action==='delete'?'删除失败，请检查 TOS 配置并重试。':'操作失败，请检查服务配置并重试。');
      if(action==='delete'){selectedSources.delete(item.id);el('speech-selected-count').textContent=`已选择 ${selectedSources.size} 条`;message.textContent='录音文件已删除，历史转写结果保留。';}
      else {const data=await r.json();message.textContent=taskLabels[data.status]||'操作成功';if(action==='query'&&data.status===STATUS.SUCCEEDED)await showTask(data.id,item.originalName);}
    }catch(e){message.textContent=e.message||'操作失败，请稍后重试。';}
    finally{busy.delete(item.id);await loadRecordings(page);}
  }
  async function showHistory(item, target, historyPage=0){
    target.replaceChildren();target.textContent='正在加载转写历史…';
    try{const path=item.id===null?`/unlinked?page=${historyPage}`:`/recordings/${encodeURIComponent(item.id)}/history?page=${historyPage}`;const r=await api(url(path));if(!r.ok)throw new Error('转写历史加载失败。');const items=await r.json();if(!document.contains(target))return;target.replaceChildren();if(!items.length)target.textContent='暂无转写历史。';
      for(const t of items){const row=document.createElement('div');row.className='speech-history-entry';const label=document.createElement('span');label.textContent=`${(t.createdAt||'未知时间').replace('T',' ')} · ${taskLabels[t.status]||'状态未知'} · ${t.id}`;row.append(label);if(t.status===STATUS.SUCCEEDED)row.append(button('查看结果',()=>showTask(t.id,item.originalName)));target.append(row);}
      const controls=document.createElement('div');controls.className='speech-pages';controls.append(button('上一页',()=>showHistory(item,target,historyPage-1),historyPage===0));const number=document.createElement('span');number.textContent=`第 ${historyPage+1} 页`;controls.append(number,button('下一页',()=>showHistory(item,target,historyPage+1),items.length<pageSize));target.append(controls);
    }catch(e){if(document.contains(target))target.textContent=e.message||'转写历史加载失败。';}
  }
  async function loadRecordings(nextPage=page){
    const seq=++requestSeq;
    try{const r=await api(url(`/recordings?page=${nextPage}`));if(!r.ok)throw new Error('录音列表加载失败，请检查服务后重试。');const items=await r.json();if(seq!==requestSeq||!document.contains(box))return;page=nextPage;for(const item of items){if(selectedSources.has(item.id)&&(item.status===STATUS.DELETED||item.transcriptionStatus!==STATUS.SUCCEEDED||selectedSources.get(item.id)!==item.transcriptionTaskId)){selectedSources.delete(item.id);message.textContent='部分已选录音的转写状态发生变化，请重新选择。';}}el('speech-selected-count').textContent=`已选择 ${selectedSources.size} 条`;list.replaceChildren();el('recordings-page').textContent=`第 ${page+1} 页`;el('recordings-prev').disabled=page===0;el('recordings-next').disabled=items.length<pageSize;
      if(!items.length){const empty=document.createElement('p');empty.className='tiny-note';empty.textContent='暂无录音，请先上传。';list.append(empty);}
      for(const item of items){const wrapper=document.createElement('div');wrapper.setAttribute('role','listitem');const row=document.createElement('div');row.className='speech-history-row';const info=document.createElement('div'),name=document.createElement('strong'),detail=document.createElement('span'),status=document.createElement('span');name.textContent=item.originalName;detail.textContent=`${(item.fileSize/1024/1024).toFixed(2)} MB · 上传于 ${(item.createdAt||'未知').replace('T',' ')}`;status.className='speech-row-status';status.textContent=`状态：${taskStatus(item)}`;info.append(name,detail,status);if(item.transcriptionStatus===STATUS.SUCCEEDED&&item.transcriptionTaskId){const pick=document.createElement('label');pick.className='speech-select';const check=document.createElement('input');check.type='checkbox';check.checked=selectedSources.get(item.id)===item.transcriptionTaskId;check.onchange=()=>{if(check.checked){if(selectedSources.size>=20){check.checked=false;message.textContent='最多选择 20 条转写结果。';return;}selectedSources.set(item.id,item.transcriptionTaskId);}else selectedSources.delete(item.id);el('speech-selected-count').textContent=`已选择 ${selectedSources.size} 条`;};pick.append(check,document.createTextNode(' 选择用于合并'));info.append(pick);}const actions=document.createElement('div');actions.className='speech-row-actions';const locked=busy.has(item.id), deleted=item.status===STATUS.DELETED, deleting=item.status===STATUS.DELETING, submitted=!!item.transcriptionTaskId, processing=active(item.transcriptionStatus)||item.status===STATUS.SUBMITTING;
        if(!deleted&&!deleting)actions.append(button(submitted?'重新转写':'开始转写',()=>mutate(item,'submit'),locked||processing||(!submitted&&item.status!==STATUS.UPLOADED)));
        if(submitted&&processing)actions.append(button('查询结果',()=>mutate(item,'query'),locked));
        if(submitted&&item.transcriptionStatus===STATUS.SUCCEEDED)actions.append(button('查看结果',()=>showTask(item.transcriptionTaskId,item.originalName),locked));
        const history=document.createElement('div');history.className='speech-row-history';history.hidden=historyOpen!==item.id;
        if(submitted)actions.append(button(historyOpen===item.id?'收起历史':'转写历史',()=>{if(historyOpen===item.id){historyOpen=null;history.hidden=true;}else{historyOpen=item.id;loadRecordings(page);}},locked));
        if(!deleted)actions.append(button(deleting?'重试删除':'删除',()=>mutate(item,'delete'),locked||processing));
        row.append(info,actions);wrapper.append(row,history);list.append(wrapper);if(!history.hidden)showHistory(item,history);
      }
    }catch(e){if(seq===requestSeq&&document.contains(box))message.textContent=e.message||'录音列表加载失败。';}
  }
  box.querySelector('.speech-unlinked').addEventListener('toggle',event=>{if(event.currentTarget.open)showHistory({id:null,originalName:'未关联录音的历史任务'},el('unlinked-history'));});
  el('speech-organize').onclick=async()=>{if(!selectedSources.size){message.textContent='请先勾选需要整理的成功转写结果。';return;}if(reviewDirty){message.textContent='请先保存正在编辑的说话人校对。';return;}if(editor.value&&!confirm('AI 整理结果将替换当前文字稿编辑区，请先确认已保存。继续吗？'))return;if(!globalThis.crypto?.randomUUID){message.textContent='当前环境无法生成请求标识，请使用安全的浏览器环境。';return;}const before=editor.value,body={taskIds:[...selectedSources.values()],expectedVersion:transcriptVersion()},signature=JSON.stringify(body);if(!pendingOrganize||pendingOrganize.signature!==signature)pendingOrganize={signature,key:crypto.randomUUID()};const b=el('speech-organize');b.disabled=true;message.textContent='AI 正在整理文字稿，请稍候…';try{const r=await api(url('/organize'),{method:'POST',headers:{'Content-Type':'application/json','Idempotency-Key':pendingOrganize.key},body:signature});if(r.status===425)throw new Error('整理仍在处理，请稍后再次点击以查询结果。');if(r.status===409){pendingOrganize=null;throw new Error('文字稿版本已变化或请求已失败，请刷新后重试。');}if(r.status===400){pendingOrganize=null;let reason='来源无效或文字超过 12000 字符，请调整选择。';try{const data=await r.json();if(typeof data.message==='string'&&data.message)reason=data.message;}catch{}throw new Error(reason);}if(r.status===503){pendingOrganize=null;throw new Error('文字稿整理提示词尚未发布，请联系管理员。');}if(!r.ok){if(r.status>=500&&r.status!==502)throw new Error('请求结果尚不确定，再次点击将查询原请求。');pendingOrganize=null;throw new Error('AI 整理失败，请检查提示词和模型配置。');}const data=await r.json();if(editor.value!==before&&!confirm('文字稿在整理期间发生了修改，确定用 AI 草稿覆盖吗？'))return;editor.value=data.content;editor.dataset.aiDraftId=data.draftId;pendingOrganize=null;message.textContent='AI 整理草稿已生成，请人工核对后保存文字稿。';}catch(e){message.textContent=e.message||'请求结果尚不确定，再次点击将查询原请求。';}finally{b.disabled=false;}};
  el('speech-assemble').onclick=async()=>{if(!selectedSources.size){message.textContent='请先勾选需要合并的成功转写结果。';return;}if(reviewDirty){message.textContent='请先保存正在编辑的说话人校对。';return;}if(editor.value&&!confirm('合并将替换当前文字稿编辑区，请先确认已保存。继续吗？'))return;const before=editor.value,btn=el('speech-assemble');btn.disabled=true;message.textContent='正在合并选中的转写内容…';try{const r=await api(url('/assemble'),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({taskIds:[...selectedSources.values()]})});if(!r.ok)throw new Error('合并失败，请检查所选结果是否仍可用。');const data=await r.json();if(editor.value!==before&&!confirm('文字稿在合并期间发生了修改，确定用合并结果覆盖吗？'))return;editor.value=data.content;delete editor.dataset.aiDraftId;message.textContent='已按勾选顺序合并到文字稿编辑区，请核对后保存；本次合并未使用 AI。';}catch(e){message.textContent=e.message||'合并失败，请稍后重试。';}finally{btn.disabled=false;}};
  el('recordings-refresh').onclick=()=>loadRecordings(page);el('recordings-prev').onclick=()=>{historyOpen=null;loadRecordings(page-1);};el('recordings-next').onclick=()=>{historyOpen=null;loadRecordings(page+1);};
  el('speech-upload').onclick=async()=>{const file=el('speech-file').files[0], b=el('speech-upload');if(!file){message.textContent='请选择录音文件。';return;}if(file.size>100*1024*1024){message.textContent='录音文件不能超过 100 MB。';return;}b.disabled=true;message.textContent='正在上传，请勿关闭页面…';try{const form=new FormData();form.append('file',file);const r=await api(url('/upload'),{method:'POST',body:form});if(!r.ok)throw new Error(r.status===503?'请先配置 TOS 凭证。':'上传失败，请检查录音格式及 TOS 配置。');el('speech-file').value='';message.textContent='上传成功，请在录音列表中开始转写。';historyOpen=null;await loadRecordings(0);}catch(e){message.textContent=e.message||'上传失败，请稍后重试。';}finally{b.disabled=false;}};
  el('chunk-preview-load').onclick=async()=>{if(!selectedTask)return;if(reviewDirty){message.textContent='请先保存说话人校对后再预览分段。';return;}const taskId=selectedTask.id,area=el('chunk-preview-list'),b=el('chunk-preview-load');b.disabled=true;area.textContent='正在加载…';try{const r=await api(url(`/${encodeURIComponent(taskId)}/chunks/preview`));if(!r.ok)throw new Error('分段预览失败，请稍后重试。');const chunks=await r.json();if(selectedTask?.id!==taskId||!document.contains(box))return;area.replaceChildren();for(const chunk of chunks){const detail=document.createElement('details'),title=document.createElement('summary'),pre=document.createElement('pre');title.textContent=`第 ${chunk.chunkIndex} 段 · 正文 ${chunk.textChars} 字符 · 上文 ${chunk.contextBefore.length} 条`;pre.style.whiteSpace='pre-wrap';pre.style.overflowWrap='anywhere';const format=u=>`[片段 ${u.segmentIndex+1}] ${u.speaker}：${u.text}`;pre.textContent='【参考上文】\n'+chunk.contextBefore.map(format).join('\n')+'\n\n【当前正文】\n'+chunk.utterances.map(format).join('\n');detail.append(title,pre);const extract=document.createElement('button'),output=document.createElement('pre');extract.type='button';extract.className='button outline';extract.textContent='AI 提取本段';output.style.whiteSpace='pre-wrap';output.style.overflowWrap='anywhere';extract.onclick=async()=>{if(!globalThis.crypto?.randomUUID){output.textContent='当前浏览器无法生成请求标识。';return;}if(reviewDirty){output.textContent='请先保存说话人校对，再重新加载分段。';return;}const fingerprint=`${taskId}:${chunk.chunkIndex}:${chunk.speakerReviewVersion}`;let key=pendingExtractions.get(fingerprint);if(!key){key=crypto.randomUUID();pendingExtractions.set(fingerprint,key);}extract.disabled=true;output.textContent='正在提取候选信息…';try{const response=await api(url(`/${encodeURIComponent(taskId)}/chunks/${chunk.chunkIndex}/extract`),{method:'POST',headers:{'Content-Type':'application/json','Idempotency-Key':key},body:JSON.stringify({speakerReviewVersion:chunk.speakerReviewVersion})});if(response.status===425)throw new Error('请求仍在处理中，请稍后点击重试查询。');if(!response.ok){if([400,404,409,502,503].includes(response.status))pendingExtractions.delete(fingerprint);let reason='提取失败，请检查配置或重新加载分段。';try{const data=await response.json();if(data.message)reason=data.message;}catch{}throw new Error(reason);}const data=await response.json();output.textContent=data.items.length?data.items.map((x,i)=>`${i+1}. [${x.type}] ${x.topic}：${x.content}\n来源片段：${x.evidenceIndices.map(n=>n+1).join('、')}${x.needsReview?' · 待确认':''}`).join('\n\n'):'本段没有提取到候选信息。';}catch(e){output.textContent=e.message||'请求结果不确定，再次点击将使用同一请求标识查询。';}finally{extract.disabled=false;}};detail.append(extract,output);area.append(detail);}if(!chunks.length)area.textContent='暂无识别正文。';}catch(e){area.textContent=e.message||'分段预览失败。';}finally{b.disabled=false;}};
  el('speech-import').onclick=()=>{if(!selectedTask?.segments?.length)return;if(editor.value&&!confirm('导入会替换当前文字稿编辑区的内容，请先确认已保存。继续吗？'))return;editor.value=selectedTask.segments.map((s,i)=>`${displaySpeaker(s,i)}：${s.text}`).join('\n');delete editor.dataset.aiDraftId;message.textContent='已导入编辑区，请核对并点击“保存文字稿”。';};
  await loadRecordings(0);
  function scheduleRefresh(){refreshTimer=setTimeout(async()=>{if(!document.contains(box))return;if(!historyOpen && busy.size===0)await loadRecordings(page);scheduleRefresh();},15000);}
  scheduleRefresh();
}

async function setupMinutes(id, role, transcriptDirty) {
  const panel=el('minutes-panel'), editor=el('minutes-content'), message=el('minutes-message');
  const buttons=['minutes-generate','minutes-save','minutes-confirm'].map(el);
  let version=0, outlineVersion=0, status='', pendingGeneration=null;
  if(role!=='ORGANIZER')el('minutes-confirm').classList.add('hidden');
  function render(data) { version=data.version;outlineVersion=data.outlineVersion;status=data.status;editor.value=data.content;editor.dataset.saved=data.content;el('minutes-status').textContent=`版本 ${version} · ${status===STATUS.CONFIRMED?'已确认':'草稿'}${data.stale?' · 来源已更新':''}`; }
  async function send(path,body) {
    buttons.forEach(b=>b.disabled=true);message.textContent='';
    try {const headers={'Content-Type':'application/json'};if(path==='/generate' && globalThis.crypto?.randomUUID){const signature=JSON.stringify(body);if(!pendingGeneration || pendingGeneration.signature!==signature)pendingGeneration={signature,key:crypto.randomUUID()};headers['Idempotency-Key']=pendingGeneration.key;}
      const response=await api(`/api/meetings/${encodeURIComponent(id)}/minutes${path}`,{method:'POST',headers,body:JSON.stringify(body)});
      if(response.status===425)throw new Error('生成请求仍在处理，请稍后点击生成以查询结果。');
      if(path==='/generate')pendingGeneration=null;
      if(response.status===409)throw new Error('文字稿、大纲或纪要已更新，请重新打开会议并确认大纲。');
      if(response.status===403)throw new Error('只有组织者可以确认纪要。');
      if(response.status===502)throw new Error('AI 生成失败，请检查模型服务。');
      if(!response.ok)throw new Error('操作失败，请检查输入和权限。');
      const data=await response.json();if(document.contains(panel)){const unchanged=data.version===version;render(data);message.textContent=unchanged?'内容和来源未变化，未新增版本。':'操作成功。';}
    } catch(error){if(document.contains(panel))message.textContent=error.message||'操作失败。';}
    finally {if(document.contains(panel))buttons.forEach(b=>b.disabled=false);}
  }
  try {const response=await api(`/api/meetings/${encodeURIComponent(id)}/minutes`);if(!document.contains(panel))return;
    if(response.ok)render(await response.json());else if(response.status===404)el('minutes-status').textContent='暂无纪要';else throw new Error('纪要加载失败。');
  } catch(error){if(document.contains(panel)){message.textContent=error.message;buttons.forEach(b=>b.disabled=true);}return;}
  el('minutes-generate').onclick=async()=>{
    if(transcriptDirty()){message.textContent='请先保存文字稿并确认大纲。';return;}
    if(editor.value && !confirm('生成新草稿将替换当前未保存的内容。继续吗？'))return;
    buttons.forEach(b=>b.disabled=true);
    try {const response=await api(`/api/meetings/${encodeURIComponent(id)}/outline`);if(!response.ok)throw new Error('请先生成并确认会议大纲。');
      const outline=await response.json();if(outline.status!==STATUS.CONFIRMED||outline.stale)throw new Error('请先确认基于最新文字稿的大纲。');
      await send('/generate',{outlineVersion:outline.version,expectedVersion:version});
    } catch(error){message.textContent=error.message||'获取大纲失败。';} finally {if(document.contains(panel))buttons.forEach(b=>b.disabled=false);}
  };
  function save(confirmMinutes) {
    if(transcriptDirty()){message.textContent='请先保存文字稿并重新确认大纲。';return;}
    if(!outlineVersion){message.textContent='请先生成纪要草稿。';return;}
    if(confirmMinutes && status!==STATUS.DRAFT){message.textContent='请先保存纪要草稿。';return;}
    if(!editor.value.trim()){message.textContent='纪要正文不能为空。';return;}
    if(!confirmMinutes && normalizeMeetingText(editor.value)===normalizeMeetingText(editor.dataset.saved||'')){message.textContent='纪要内容未变化，无需保存。';return;}
    send('',{outlineVersion,expectedVersion:version,content:editor.value,confirm:confirmMinutes});
  }
  el('minutes-save').onclick=()=>save(false);el('minutes-confirm').onclick=()=>save(true);
}

async function setupOutline(id, role, transcriptVersion, transcriptDirty) {
  const panel=el('outline-panel'), items=el('outline-items'), message=el('outline-message');
  let outlineVersion=0, baseTranscript=0, status=STATUS.DRAFT, savedItems=[], pendingGeneration=null;
  const buttons=['outline-generate','outline-add','outline-save','outline-confirm'].map(el);
  if(role!=='ORGANIZER') el('outline-confirm').classList.add('hidden');
  function render(data) {
    outlineVersion=data.version; baseTranscript=data.transcriptVersion; status=data.status;
    savedItems=data.items.map(item=>item.trim());
    el('outline-status').textContent=`版本 ${data.version} · ${data.status===STATUS.CONFIRMED?'已确认':'草稿'}${data.stale?' · 文字稿已更新':''}`;
    items.replaceChildren(); data.items.forEach(addItem);
  }
  function addItem(text='') {
    const row=document.createElement('div'); row.className='outline-edit-row';
    const input=document.createElement('input'); input.value=text; input.maxLength=200; input.placeholder='大纲条目';
    const up=document.createElement('button'); up.type='button'; up.className='button outline'; up.textContent='↑'; up.setAttribute('aria-label','上移'); up.onclick=()=>{if(row.previousElementSibling)items.insertBefore(row,row.previousElementSibling);};
    const remove=document.createElement('button'); remove.type='button'; remove.className='button outline'; remove.textContent='删除'; remove.onclick=()=>row.remove();
    row.append(input,up,remove); items.append(row);
  }
  async function send(path,body) {
    buttons.forEach(b=>b.disabled=true); message.textContent='';
    try {
      const headers={'Content-Type':'application/json'};if(path==='/generate' && globalThis.crypto?.randomUUID){const signature=JSON.stringify(body);if(!pendingGeneration || pendingGeneration.signature!==signature)pendingGeneration={signature,key:crypto.randomUUID()};headers['Idempotency-Key']=pendingGeneration.key;}
      const response=await api(`/api/meetings/${encodeURIComponent(id)}/outline${path}`,{method:'POST',headers,body:JSON.stringify(body)});
      if(response.status===425)throw new Error('生成请求仍在处理，请稍后点击生成以查询结果。');
      if(path==='/generate')pendingGeneration=null;
      if(response.status===409) throw new Error('文字稿或大纲已更新，请重新打开会议后再试。');
      if(response.status===403) throw new Error('只有组织者可以确认大纲。');
      if(response.status===502) throw new Error('模型生成失败，请检查模型服务配置后重试。');
      if(!response.ok) throw new Error('操作失败，请检查文字稿长度和大纲内容。');
      const data=await response.json(); if(document.contains(panel)){const unchanged=data.version===outlineVersion;render(data);message.textContent=unchanged?'内容和来源未变化，未新增版本。':'操作成功。';}
    } catch(error) { if(document.contains(panel)) message.textContent=error.message || '操作失败。'; }
    finally { if(document.contains(panel)) buttons.forEach(b=>b.disabled=false); }
  }
  try {
    const response=await api(`/api/meetings/${encodeURIComponent(id)}/outline`);
    if(!document.contains(panel)) return;
    if(response.ok) render(await response.json());
    else if(response.status===404) el('outline-status').textContent='暂无大纲';
    else { el('outline-status').textContent='加载失败'; buttons.forEach(b=>b.disabled=true); return; }
  } catch { if(document.contains(panel)){el('outline-status').textContent='加载失败';buttons.forEach(b=>b.disabled=true);} return; }
  el('outline-add').onclick=()=>addItem();
  el('outline-generate').onclick=()=>{
    if(transcriptDirty() || transcriptVersion()===0) { message.textContent='请先保存文字稿。'; return; }
    if(outlineVersion && !confirm('生成新草稿会保留已保存的历史版本，当前未保存的编辑将丢失。继续吗？')) return;
    send('/generate',{transcriptVersion:transcriptVersion(),expectedVersion:outlineVersion});
  };
  function save(confirm) {
    if(transcriptDirty()){message.textContent='请先保存文字稿，再更新大纲。';return;}
    if(!baseTranscript){message.textContent='请先生成大纲草稿。';return;}
    if(confirm && status!==STATUS.DRAFT){message.textContent='请先保存新的草稿，再确认。';return;}
    const values=Array.from(items.querySelectorAll('input')).map(input=>input.value.trim());
    if(!values.length || values.some(value=>!value)){message.textContent='请至少填写一个非空条目。';return;}
    if(!confirm && baseTranscript===transcriptVersion() && JSON.stringify(values)===JSON.stringify(savedItems)){message.textContent='大纲内容未变化，无需保存。';return;}
    send('',{transcriptVersion:baseTranscript,expectedVersion:outlineVersion,items:values,confirm});
  }
  el('outline-save').onclick=()=>save(false);
  el('outline-confirm').onclick=()=>save(true);
}

function setupChat() {
  const input = el('chat-input');
  document.querySelectorAll('.suggestion').forEach(button => button.addEventListener('click', () => {
    input.value = button.dataset.prompt;
    input.focus();
  }));
  el('chat-form').addEventListener('submit', async event => {
    event.preventDefault();
    const question = input.value.trim();
    if (!question || busy) return;
    busy = true;
    el('send').disabled = true;
    el('chat-error').textContent = '';
    const list = el('chat-messages');
    list.querySelector('.assistant-welcome')?.remove();
    const user = document.createElement('div'); user.className='message user'; user.textContent=question; list.append(user);
    const reply = document.createElement('div'); reply.className='message assistant'; reply.textContent='正在思考…'; list.append(reply);
    input.value = '';
    try {
      const response = await api('/api/chat', {method:'POST', headers:{'Content-Type':'application/json','Accept':'text/event-stream'},
        body:JSON.stringify({platform:'deepseek',modelName:'deepseek-chat',temperature:0.5,info:question})});
      if (!response.ok || !response.body) throw new Error('聊天请求失败，请检查模型配置和服务状态。');
      const reader = response.body.getReader(); const decoder = new TextDecoder();
      let buffer = '', output = '';
      const consume = line => {
        if (!line.startsWith('data:')) return;
        let data = line.slice(5); if (data.startsWith(' ')) data = data.slice(1);
        if (data === '[DONE]') return;
        output += data;
        reply.textContent = output || '正在思考…';
        list.scrollTop = list.scrollHeight;
      };
      while (true) {
        const {value, done} = await reader.read();
        buffer += decoder.decode(value || new Uint8Array(), {stream:!done});
        const lines = buffer.split(/\r?\n/); buffer = lines.pop();
        lines.forEach(consume);
        if (done) { if (buffer) consume(buffer); break; }
      }
      if (!output) reply.textContent = '服务未返回内容，请检查模型和接口配置。';
    } catch (error) {
      reply.textContent = '未能获得回复。';
      const errorElement = el('chat-error'); if (errorElement) errorElement.textContent = error.message || '网络异常，请稍后重试。';
    } finally {
      busy = false;
      const send = el('send'); if (send) send.disabled = false;
    }
  });
}

// 版本内容使用 textContent 或 value 展示，不把管理员输入解释为 HTML。
async function setupPrompts() {
  const root=el('content');
  root.innerHTML=`<div class="page-heading"><div><h1>提示词管理</h1><p class="subtitle">保存新版本后需发布才能生效；发布历史版本即可回滚。</p></div><button class="button outline" id="prompt-back">返回管理后台</button></div>
    <section class="panel"><label class="form-label">场景<select id="prompt-scenario"></select></label><p id="prompt-state" class="tiny-note"></p><p id="prompt-error" class="error" role="alert"></p>
    <label class="form-label">提示词内容<textarea id="prompt-content" rows="8" maxlength="10000"></textarea></label><label class="form-label">修改原因<input id="prompt-note" maxlength="500"></label><button class="button primary" id="prompt-create">保存为新版本</button></section>
    <section class="panel"><h3>历史版本</h3><div id="prompt-versions"></div></section>
    <section class="panel"><h3>发布记录（最近 100 条）</h3><div id="prompt-releases"></div></section>
    <section class="panel"><h3>最近 AI 调用</h3><p class="tiny-note">调用详情包含会议敏感内容，仅管理员可查看。</p><div id="prompt-calls"></div><div id="call-detail"></div></section>`;
  let current=null;
  const error=el('prompt-error');
  el('prompt-back').onclick=()=>setupAdmin();
  async function req(path,options={}) { const response=await api(path,options); if(!response.ok){let msg='请求失败，请检查服务和数据库初始化';try{msg=(await response.json()).message||msg;}catch{}throw new Error(msg);} return response.status===204?null:response.json(); }
  const post=body=>({method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
  function row(parent,content){const div=document.createElement('div');div.className='meeting-row';const span=document.createElement('span');span.textContent=content;div.append(span);parent.append(div);return div;}
  async function load() {
    error.textContent='';
    try {
      const scenarios=await req('/api/admin/prompts'); if(!document.contains(root)||!el('prompt-scenario'))return;
      const select=el('prompt-scenario'), previous=select.value; select.replaceChildren();
      scenarios.forEach(x=>select.add(new Option(x.name+'（'+x.code+'）',x.code)));
      if(scenarios.some(x=>x.code===previous))select.value=previous;
      current=scenarios.find(x=>x.code===select.value);
      if(!current){el('prompt-state').textContent='暂无场景，请执行数据库初始化脚本。';return;}
      const code=encodeURIComponent(current.code);
      const [versions,releases,calls]=await Promise.all([req(`/api/admin/prompts/${code}/versions`),req(`/api/admin/prompts/${code}/releases`),req('/api/admin/prompts/calls?page=1&size=20')]);
      if(!el('prompt-versions'))return;
      const active=versions.find(v=>v.id===current.activeVersionId);
      el('prompt-state').textContent=active?`当前生效：版本 ${active.version}（ID ${active.id}）`:'尚未发布';
      const list=el('prompt-versions');list.replaceChildren();if(!versions.length)list.textContent='暂无版本';
      versions.forEach(v=>{const item=row(list,`版本 ${v.version} · ${v.changeNote} · ${v.createdAt||''}${v.id===current.activeVersionId?' · 生效中':''}`);
        const edit=document.createElement('button');edit.className='button outline';edit.textContent='载入编辑';edit.onclick=()=>{el('prompt-content').value=v.content;el('prompt-note').value='';};item.append(edit);
        if(v.id!==current.activeVersionId){const publish=document.createElement('button');publish.className='button outline';publish.textContent='发布此版本';publish.onclick=async()=>{const reason=prompt('请输入发布或回滚原因');if(!reason?.trim())return;publish.disabled=true;try{await req(`/api/admin/prompts/${code}/publish`,post({versionId:v.id,expectedActiveVersionId:current.activeVersionId,reason:reason.trim()}));await load();}catch(e){error.textContent=e.message;}finally{publish.disabled=false;}};item.append(publish);}});
      const audit=el('prompt-releases');audit.replaceChildren();if(!releases.length)audit.textContent='暂无发布记录';releases.forEach(x=>row(audit,`${x.createdAt||''} · 用户 ${x.actorId} · 版本 ID ${x.oldVersionId??'无'} → ${x.newVersionId} · ${x.reason}`));
      const callList=el('prompt-calls');callList.replaceChildren();if(!calls.length)callList.textContent='暂无调用记录';calls.forEach(x=>{const item=row(callList,`${x.createdAt||''} · ${x.scenario} · ${x.provider||'供应商未知'}/${x.modelName||'模型未知'} · 提示词版本 ID ${x.promptVersionId} · 模型 ${STATUS_LABEL[x.status]||x.status} · 业务 ${STATUS_LABEL[x.businessStatus]||'历史数据未知'}${x.resultVersion!=null?' · 产物版本 '+x.resultVersion:''} · Token 输入 ${x.inputTokens??'未知'} / 输出 ${x.outputTokens??'未知'} / 缓存 ${x.cachedTokens??'未知'} · ${x.durationMs??'—'} ms`);
        const button=document.createElement('button');button.type='button';button.className='button outline';button.textContent='查看输入输出';button.onclick=async()=>{button.disabled=true;try{const data=await req(`/api/admin/prompts/calls/${x.id}/content`);const detail=el('call-detail');if(!detail)return;detail.replaceChildren();const title=document.createElement('h3');title.textContent=`调用 ${x.id} 内容`;detail.append(title);for(const [label,value] of [['系统提示词',data.systemContent],['用户输入',data.userContent],['上下文工具调用记录',data.toolTrace??'无工具调用记录'],['模型原始输出',data.outputContent??'未收到输出']]){const heading=document.createElement('h4');heading.textContent=label;const pre=document.createElement('pre');pre.style.whiteSpace='pre-wrap';pre.style.overflowWrap='anywhere';pre.textContent=value;detail.append(heading,pre);}}catch(e){error.textContent=e.message;}finally{button.disabled=false;}};item.append(button);});
    } catch(e){if(document.contains(error))error.textContent=e.message;}
  }
  el('prompt-scenario').onchange=()=>load();
  el('prompt-create').onclick=async()=>{const button=el('prompt-create');if(!current)return;button.disabled=true;error.textContent='';try{await req(`/api/admin/prompts/${encodeURIComponent(current.code)}/versions`,post({content:el('prompt-content').value,changeNote:el('prompt-note').value.trim()}));el('prompt-note').value='';await load();}catch(e){error.textContent=e.message;}finally{button.disabled=false;}};
  await load();
}
