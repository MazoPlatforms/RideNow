const $ = (selector, root = document) => root.querySelector(selector);

function labelFor(select) {
  const label = select.closest('label');
  return label ? label.childNodes[0].textContent.trim() : select.getAttribute('aria-label') || select.name || 'Choose an option';
}

function enhanceSelect(select) {
  if (select.dataset.customized === 'true') return;
  select.dataset.customized = 'true';
  const wrapper = document.createElement('span');
  wrapper.className = 'custom-select';
  select.after(wrapper);
  wrapper.append(select);
  select.classList.add('custom-select-source');
  select.tabIndex = -1;
  select.setAttribute('aria-hidden', 'true');

  const trigger = document.createElement('button');
  trigger.type = 'button';
  trigger.className = 'input custom-select-trigger';
  trigger.setAttribute('aria-haspopup', 'listbox');
  trigger.setAttribute('aria-expanded', 'false');
  trigger.setAttribute('aria-label', labelFor(select));
  const value = document.createElement('span');
  const chevron = document.createElement('span');
  chevron.className = 'custom-select-chevron';
  chevron.setAttribute('aria-hidden', 'true');
  chevron.textContent = '⌄';
  trigger.append(value, chevron);

  const menu = document.createElement('div');
  menu.className = 'custom-select-menu';
  menu.role = 'listbox';
  menu.setAttribute('aria-label', labelFor(select));
  menu.hidden = true;
  wrapper.append(trigger, menu);

  const sync = () => {
    const selected = select.selectedOptions[0];
    value.textContent = selected?.textContent || 'Choose an option';
    trigger.disabled = select.disabled;
    trigger.classList.toggle('is-placeholder', !select.value);
    menu.replaceChildren();
    [...select.options].forEach((option, index) => {
      const item = document.createElement('button');
      item.type = 'button';
      item.className = 'custom-select-option';
      item.role = 'option';
      item.textContent = option.textContent;
      item.disabled = option.disabled || option.hidden;
      item.setAttribute('aria-selected', String(option.selected));
      item.dataset.index = String(index);
      item.addEventListener('click', () => {
        select.selectedIndex = index;
        select.dispatchEvent(new Event('input', { bubbles: true }));
        select.dispatchEvent(new Event('change', { bubbles: true }));
        close();
        trigger.focus();
      });
      menu.append(item);
    });
  };
  const close = () => {
    menu.hidden = true;
    trigger.setAttribute('aria-expanded', 'false');
    wrapper.classList.remove('is-open');
  };
  const open = (move = false) => {
    if (trigger.disabled) return;
    document.querySelectorAll('.custom-select.is-open').forEach((node) => {
      if (node !== wrapper) {
        node.classList.remove('is-open');
        node.querySelector('.custom-select-menu').hidden = true;
        node.querySelector('.custom-select-trigger').setAttribute('aria-expanded', 'false');
      }
    });
    sync();
    menu.hidden = false;
    trigger.setAttribute('aria-expanded', 'true');
    wrapper.classList.add('is-open');
    if (move) (menu.querySelector('[aria-selected=true]:not(:disabled)') || menu.querySelector('.custom-select-option:not(:disabled)'))?.focus();
  };
  trigger.addEventListener('click', () => menu.hidden ? open() : close());
  trigger.addEventListener('keydown', (event) => {
    if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) {
      event.preventDefault();
      open(true);
      if (event.key === 'ArrowUp') menu.querySelector('.custom-select-option:not(:disabled):last-of-type')?.focus();
    }
  });
  menu.addEventListener('keydown', (event) => {
    const options = [...menu.querySelectorAll('.custom-select-option:not(:disabled)')];
    const current = options.indexOf(document.activeElement);
    if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(); trigger.focus(); }
    else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      options[Math.max(0, Math.min(options.length - 1, current + (event.key === 'ArrowDown' ? 1 : -1)))]?.focus();
    } else if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault(); (event.key === 'Home' ? options[0] : options.at(-1))?.focus();
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault(); document.activeElement.click();
    } else if (event.key.length === 1 && /\S/.test(event.key)) {
      const match = options.find((option) => option.textContent.trim().toLowerCase().startsWith(event.key.toLowerCase()));
      match?.focus();
    }
  });
  select.addEventListener('change', sync);
  new MutationObserver(sync).observe(select, { childList: true, subtree: true, attributes: true });
  sync();
}

export function enhanceControls(root = document) {
  root.querySelectorAll('select').forEach(enhanceSelect);
}

export function confirmSheet(message, { title = 'Confirm action', confirmText = 'Continue', danger = false } = {}) {
  return new Promise((resolve) => {
    const dialog = $('#action-sheet');
    $('#sheet-title').textContent = title;
    $('#sheet-message').textContent = message;
    $('#sheet-field').hidden = true;
    const confirm = $('#sheet-confirm');
    confirm.textContent = confirmText;
    confirm.className = danger ? 'btn-sheet btn-sheet-danger' : 'btn-sheet btn-sheet-primary';
    const finish = (result) => { dialog.close(); resolve(result); };
    $('#sheet-cancel').onclick = () => finish(false);
    confirm.onclick = () => finish(true);
    dialog.oncancel = () => resolve(false);
    dialog.showModal();
    confirm.focus();
  });
}

export function promptSheet(message, { title = 'Enter a value', label = 'Value', confirmText = 'Continue' } = {}) {
  return new Promise((resolve) => {
    const dialog = $('#action-sheet');
    const field = $('#sheet-field');
    const input = $('#sheet-input');
    $('#sheet-title').textContent = title;
    $('#sheet-message').textContent = message;
    field.hidden = false;
    field.querySelector('span').textContent = label;
    input.value = '';
    input.required = true;
    input.maxLength = 500;
    const confirm = $('#sheet-confirm');
    confirm.textContent = confirmText;
    confirm.className = 'btn-sheet btn-sheet-primary';
    const finish = (result) => { dialog.close(); resolve(result); };
    $('#sheet-cancel').onclick = () => finish(null);
    confirm.onclick = () => input.reportValidity() && finish(input.value);
    dialog.oncancel = () => resolve(null);
    dialog.showModal();
    input.focus();
  });
}

document.addEventListener('click', (event) => {
  if (!event.target.closest('.custom-select')) {
    document.querySelectorAll('.custom-select.is-open').forEach((wrapper) => {
      wrapper.classList.remove('is-open');
      wrapper.querySelector('.custom-select-menu').hidden = true;
      wrapper.querySelector('.custom-select-trigger').setAttribute('aria-expanded', 'false');
    });
  }
});
