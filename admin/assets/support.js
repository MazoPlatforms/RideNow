import { $, action, api, badge, busy, date, el, empty, row, short } from './api.js';

const base = '/v1/admin/support';
const kinds = { TICKET: 'Ticket', CHAT: 'Chat', COMPLAINT: 'Complaint', SAFETY: 'Safety report' };
const states = { OPEN: 'Open', IN_PROGRESS: 'In progress', RESOLVED: 'Resolved' };

export function supportQueue(app) {
  const dialog = $('#detail-dialog');
  const tbody = $('#support-records');
  const more = $('#support-more');
  const stateFilter = $('[name=support-status]');
  const kindFilter = $('[name=support-kind]');
  let next = null;
  let listRequest = 0;

  const path = (resource, filters = {}) => base + resource + '?' + new URLSearchParams({ app, ...filters });
  async function list(cursor = null) {
    const request = ++listRequest;
    const result = await api(path('/tickets', {
      ...(stateFilter.value && { status: stateFilter.value }),
      ...(kindFilter.value && { kind: kindFilter.value }), ...cursor,
    }));
    if (request !== listRequest) return;
    if (!cursor) tbody.replaceChildren();
    for (const ticket of result.items) {
      tbody.append(row([el('span', ticket.subject, 'break-words'), ticket.customer_name || short(ticket.user_id),
        kinds[ticket.kind], badge(ticket.status), date(ticket.updated_at), action('Open', () => openThread(ticket))]));
    }
    if (!result.items.length && !cursor) empty(tbody, 6, 'No matching support requests.');
    next = result.next;
    more.hidden = !next;
  }

  async function openThread(summary) {
    const resource = '/tickets/' + summary.id;
    const content = el('div', null, 'space-y-4');
    const heading = el('p');
    const error = el('p', null, 'notice error');
    error.setAttribute('role', 'alert');
    error.hidden = true;
    const messages = el('div', null, 'space-y-3');
    const form = el('form', null, 'space-y-3');
    const replyLabel = el('label', 'Reply to customer', 'field');
    const reply = el('textarea', null, 'input');
    reply.rows = 4;
    reply.required = true;
    reply.maxLength = 4000;
    replyLabel.append(reply);
    const submit = el('button', 'Send reply', 'btn-primary');
    form.append(replyLabel, submit);
    const actions = el('div', null, 'flex flex-wrap gap-2');
    let ticket, older, pending, statusRequest, refreshRequest = 0;
    let entries = new Map();

    function report(failure) {
      error.textContent = failure.message;
      error.hidden = false;
    }

    function perform(button, fn) {
      return busy(button, async () => {
        error.hidden = true;
        try { await fn(); }
        catch (failure) { report(failure); if (failure.status === 401) throw failure; }
      });
    }

    function attachment(file) {
      if (file.scan_status !== 'CLEAN')
        return el('p', file.name + (file.scan_status === 'PENDING' ? ' · Scanning' : ' · Unavailable'), 'text-sm break-all');
      const line = el('div', null, 'flex flex-wrap items-center gap-2');
      line.append(el('span', file.name, 'text-sm break-all'), action('Download', async () => {
        const { downloadUrl } = await api(path('/attachments/' + file.id));
        const url = new URL(downloadUrl);
        if ((url.protocol !== 'https:' && url.origin !== location.origin) || url.username || url.password)
          throw new Error('The server returned an invalid download link.');
        const link = el('a', 'Open download', 'btn-small');
        link.href = url.href;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        line.querySelector('a')?.remove();
        line.append(link);
        link.focus();
      }));
      return line;
    }

    function render() {
      heading.replaceChildren(el('span', kinds[ticket.kind] + ' · ' + (summary.customer_name || short(ticket.user_id)) + ' '), badge(ticket.status));
      form.hidden = ticket.status === 'RESOLVED';
      actions.replaceChildren(action('Refresh conversation', () => perform(null, () => load())));
      for (const [state, label] of Object.entries(states)) {
        if (state === ticket.status) continue;
        const buttonLabel = state === 'OPEN' ? (ticket.status === 'RESOLVED' ? 'Reopen' : 'Mark open') :
          state === 'RESOLVED' ? 'Resolve' : label;
        actions.append(action(buttonLabel, () => perform(null, async () => {
          if (statusRequest?.state !== state) statusRequest = { state, key: crypto.randomUUID() };
          await api(base + resource, { method: 'PATCH', body: { app, status: state }, key: statusRequest.key });
          statusRequest = null;
          await load();
          await list();
        })));
      }
      messages.replaceChildren();
      if (older) messages.append(action('Load older messages', () => perform(null, () => load(older))));
      for (const item of [...entries.values()].sort((a, b) => a.created_at.localeCompare(b.created_at))) {
        const block = el('article', null, 'rounded-xl border border-slate-100 p-4 space-y-2');
        block.append(el('p', (item.sender === 'SUPPORT' ? 'Support' : 'Customer') + ' · ' + date(item.created_at), 'text-xs text-slate-500'));
        if (item.body) block.append(el('p', item.body, 'whitespace-pre-wrap break-words text-sm'));
        block.append(...item.attachments.map(attachment));
        messages.append(block);
      }
    }

    async function load(cursor = null) {
      const request = ++refreshRequest;
      const result = await api(path(resource, cursor || {}));
      if (request !== refreshRequest) return;
      ticket = result.ticket;
      if (!entries.size || cursor) older = result.next;
      for (const item of result.messages) entries.set(item.id, item);
      render();
    }

    form.addEventListener('submit', event => {
      event.preventDefault();
      const body = reply.value.trim();
      if (!body) return;
      perform(submit, async () => {
        if (pending?.body !== body) pending = { body, key: crypto.randomUUID() };
        await api(base + resource + '/messages', { method: 'POST', body: { app, body }, key: pending.key });
        pending = null;
        if (reply.value.trim() === body) reply.value = '';
        await load();
        await list();
      });
    });

    await load();
    content.append(heading, error, actions, messages, form);
    $('#dialog-title').textContent = ticket.subject;
    $('#dialog-content').replaceChildren(content);
    dialog.showModal();
    const timer = setInterval(() => { if (!document.hidden) load().catch(report); }, 12000);
    dialog.addEventListener('close', () => clearInterval(timer), { once: true });
  }

  for (const filter of [stateFilter, kindFilter]) filter.addEventListener('change', () => busy(null, () => list()));
  more.addEventListener('click', () => busy(more, () => list(next)));
  return { refresh: () => list() };
}
