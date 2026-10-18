/*
 * Vitrine bridge for fragrantica.com/search/.
 * Injected at document start (before the site's own scripts).
 *
 * Native → JS:  window.VitrineBridge.<method>(...)
 * JS → Native:  window.VitrineNative.onState(json)   (addJavascriptInterface)
 *
 * The bridge only reads the page and clicks the site's own controls; it never
 * issues requests of its own. Selectors drift; patch SEL below.
 */
(function () {
  'use strict';
  if (window.VitrineBridge) return;
  // Same-origin iframes get this script too; only the top page talks to native.
  if (window.top !== window) return;

  var SEL = {
    // One element per filter section in the (hidden) sidebar.
    section: 'aside > div > div',
    // Section heading: collapsible panels use a button, fixed ones an h3/label.
    sectionTitle: ':scope > button[aria-expanded] > span, :scope > div > h3, :scope > div > label',
    sectionToggle: ':scope > button[aria-expanded]',
    mainSearch: 'main .ais-SearchBox input[type="search"], main .ais-SearchBox input',
    sortSelect: '.ais-SortBy-select',
    ratingItem: '.ais-RatingMenu-item',
    ratingLink: '.ais-RatingMenu-link, a',
    ratingCount: '.ais-RatingMenu-count',
    ratingSelected: 'ais-RatingMenu-item--selected',
    range: '.ais-RangeInput',
    rangeInputs: 'input[type="number"]',
    listItem: 'li',
    labelText: '.ais-RefinementList-labelText, .ais-NumericMenu-labelText',
    count: '.ais-RefinementList-count',
    showMore: '.ais-RefinementList-showMore',
    facetSearch: '.ais-RefinementList-searchBox input',
    hit: 'a.prefumeHbox',
    // "Show more results" under the grid; matched by text within these buttons.
    showMoreHits: '.ais-InfiniteHits button',
    showMoreHitsText: /more results/i,
    // The results header has List/Grid toggles; the skin needs Grid.
    gridViewButton: '.ais-InfiniteHits button[aria-label="Grid view"][aria-pressed="false"]',
    // Vue 3 root whose vnode tree holds the InstantSearch instance.
    vueRoot: '#app'
  };

  var root = document.documentElement;
  var isSearch = /^\/search(\/|$)/.test(location.pathname);
  var isPerfume = /^\/perfume\//.test(location.pathname);
  root.classList.add('vitrine');
  if (isSearch) root.classList.add('vitrine-search');
  if (isPerfume) root.classList.add('vitrine-perfume');

  function post(state) {
    try {
      if (window.VitrineNative) window.VitrineNative.onState(JSON.stringify(state));
    } catch (e) { /* native side gone */ }
  }

  if (!isSearch) {
    var titleVisible = true, watching = false;
    var notify = function () {
      var st = { page: isPerfume ? 'perfume' : 'other', url: location.href };
      if (isPerfume) {
        // h1 is "Name Brand <span>for men</span>"; the brand also sits in [itemprop=brand].
        var h1 = document.querySelector('#toptop h1, h1[itemprop="name"]');
        var brand = text(document.querySelector('[itemprop="brand"] [itemprop="name"]'));
        var name = '';
        if (h1) {
          var first = null;
          for (var n = h1.firstChild; n; n = n.nextSibling) {
            if (n.nodeType === 3) { name += n.textContent; if (!first) first = n; }
          }
          name = name.replace(/\s+/g, ' ').trim();
          if (brand && name.slice(-brand.length) === brand) {
            name = name.slice(0, -brand.length).trim();
            // The brand is shown on its own line in the hero; keep the title to the name.
            if (first) first.textContent = name + ' ';
          }
        }
        st.title = name;
        st.brand = brand;
        st.titleVisible = titleVisible;
        // Native shows the name in its bar only once the big title has scrolled away.
        if (h1 && !watching && window.IntersectionObserver) {
          watching = true;
          new IntersectionObserver(function (entries) {
            var v = entries[entries.length - 1].isIntersecting;
            if (v !== titleVisible) { titleVisible = v; notify(); }
          }).observe(h1);
        }
      }
      post(st);
    };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', notify);
    else notify();
    window.VitrineBridge = {
      readFacets: function () { notify(); return null; }
    };
    return;
  }

  // ---------------------------------------------------------------- total hits
  // Primary: the page's InstantSearch instance (exact nbHits, render events).
  // Fallback: passively read nbHits from the page's own Algolia XHR responses.
  var search = null;
  var sniffedTotal = null;

  (function sniffXhr() {
    var open = XMLHttpRequest.prototype.open;
    var send = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (m, url) {
      this.__vitrineUrl = String(url || '');
      return open.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function () {
      var x = this;
      if (/algolia/.test(x.__vitrineUrl) && /\/queries/.test(x.__vitrineUrl)) {
        x.addEventListener('load', function () {
          try {
            var res = JSON.parse(x.responseText).results || [];
            for (var i = 0; i < res.length; i++) {
              if (res[i].hitsPerPage > 0 && typeof res[i].nbHits === 'number') {
                sniffedTotal = res[i].nbHits;
                schedule();
                return;
              }
            }
          } catch (e) { /* not JSON */ }
        });
      }
      return send.apply(this, arguments);
    };
  })();

  function findSearch() {
    var el = document.querySelector(SEL.vueRoot);
    if (!el || !el._vnode) return null;
    var seen = new Set(), found = null;
    function isIS(v) { return v && typeof v === 'object' && v.mainIndex && typeof v.addWidgets === 'function'; }
    function walk(inst) {
      if (!inst || found || seen.has(inst)) return;
      seen.add(inst);
      var bags = [inst.provides, inst.data, inst.setupState];
      for (var b = 0; b < bags.length; b++) {
        var bag = bags[b];
        if (!bag) continue;
        var keys = Reflect.ownKeys(bag);
        for (var k = 0; k < keys.length; k++) {
          var v; try { v = bag[keys[k]]; } catch (e) { continue; }
          if (isIS(v)) { found = v; return; }
        }
      }
      walkVNode(inst.subTree);
    }
    function walkVNode(vn) {
      if (!vn || found) return;
      if (vn.component) walk(vn.component);
      if (Array.isArray(vn.children)) {
        for (var i = 0; i < vn.children.length; i++) {
          var c = vn.children[i];
          if (c && typeof c === 'object') walkVNode(c);
        }
      }
      if (vn.suspense) walkVNode(vn.suspense.activeBranch);
    }
    try { walkVNode(el._vnode); } catch (e) { /* Vue internals changed */ }
    return found;
  }

  function total() {
    if (search) {
      try {
        var r = search.mainIndex.getResults();
        if (r && typeof r.nbHits === 'number') return r.nbHits;
      } catch (e) { /* fall through */ }
    }
    return sniffedTotal;
  }

  // ---------------------------------------------------------------- DOM reads
  function text(el) { return el ? (el.textContent || '').replace(/\s+/g, ' ').trim() : ''; }
  function num(s) { var n = parseInt(String(s).replace(/[^\d]/g, ''), 10); return isNaN(n) ? null : n; }

  function sections() {
    var out = [];
    var els = document.querySelectorAll(SEL.section);
    for (var i = 0; i < els.length; i++) {
      var title = text(els[i].querySelector(SEL.sectionTitle));
      if (title) out.push({ el: els[i], title: title });
    }
    return out;
  }

  function sectionByTitle(title) {
    var t = String(title).toLowerCase();
    var all = sections();
    for (var i = 0; i < all.length; i++) if (all[i].title.toLowerCase() === t) return all[i].el;
    return null;
  }

  function itemLabel(li, input) {
    var lt = li.querySelector(SEL.labelText);
    if (lt) return text(lt);
    // Plain markup (e.g. Gender): <label><input><span>label</span><span>count</span></label>
    var spans = li.querySelectorAll('label > span');
    if (spans.length) return text(spans[0]);
    return input ? input.value : text(li);
  }

  function itemCount(li) {
    var c = li.querySelector(SEL.count);
    if (c) return num(text(c));
    var spans = li.querySelectorAll('label > span');
    return spans.length > 1 ? num(text(spans[spans.length - 1])) : null;
  }

  function readSection(s) {
    var el = s.el;
    var p = { title: s.title, type: 'list', items: [], searchable: false, canShowMore: false, showingMore: false, query: '' };

    if (el.querySelector(SEL.sortSelect)) return null; // sort is reported separately

    var range = el.querySelector(SEL.range);
    if (range) {
      var ins = range.querySelectorAll(SEL.rangeInputs);
      p.type = 'range';
      p.range = {
        min: ins[0] ? num(ins[0].min) : null,
        max: ins[0] ? num(ins[0].max) : null,
        from: ins[0] && ins[0].value ? num(ins[0].value) : null,
        to: ins[1] && ins[1].value ? num(ins[1].value) : null
      };
      return p;
    }

    var ratings = el.querySelectorAll(SEL.ratingItem);
    if (ratings.length) {
      p.type = 'rating';
      for (var r = 0; r < ratings.length; r++) {
        var a = ratings[r].querySelector(SEL.ratingLink);
        var label = a ? (a.getAttribute('aria-label') || text(a)) : text(ratings[r]);
        p.items.push({
          value: label,
          label: label,
          count: num(text(ratings[r].querySelector(SEL.ratingCount))),
          checked: ratings[r].classList.contains(SEL.ratingSelected)
        });
      }
      return p;
    }

    var lis = el.querySelectorAll(SEL.listItem);
    for (var i = 0; i < lis.length; i++) {
      var input = lis[i].querySelector('input[type="checkbox"], input[type="radio"]');
      if (!input) continue;
      if (input.type === 'radio') p.type = 'radio';
      var lbl = itemLabel(lis[i], input);
      p.items.push({
        value: input.type === 'checkbox' && input.value && input.value !== 'on' ? input.value : lbl,
        label: lbl,
        count: itemCount(lis[i]),
        checked: !!input.checked
      });
    }
    var fs = el.querySelector(SEL.facetSearch);
    if (fs) { p.searchable = true; p.query = fs.value || ''; }
    var more = el.querySelector(SEL.showMore);
    if (more && !more.disabled) {
      p.canShowMore = true;
      p.showingMore = /less/i.test(text(more));
    }
    return p.items.length || p.searchable ? p : null;
  }

  function readSort() {
    var sel = document.querySelector(SEL.sortSelect);
    if (!sel) return null;
    var opts = [];
    for (var i = 0; i < sel.options.length; i++) opts.push({ value: sel.options[i].value, label: text(sel.options[i]) });
    return { options: opts, selected: sel.value };
  }

  function readFacets() {
    var panels = [];
    var all = sections();
    for (var i = 0; i < all.length; i++) {
      var p = readSection(all[i]);
      if (p) panels.push(p);
    }
    var q = document.querySelector(SEL.mainSearch);
    return {
      page: 'search',
      url: location.href,
      total: total(),
      hits: document.querySelectorAll(SEL.hit).length,
      hitList: window.HyperExtract ? window.HyperExtract.hits() : [],
      // uiState has the query even when it came from the URL (the input may be empty).
      query: ((uiIndexState() || {}).query) || (q ? q.value : ''),
      sort: readSort(),
      panels: panels,
      refinements: readRefinements(panels)
    };
  }

  // ---------------------------------------------------------------- active refinements
  // Source of truth is the page's InstantSearch uiState: at 0 results the site
  // drops checked values from its lists, but the uiState still has them.
  // Attribute → panel title; seeded, then learned from checked items.
  var ATTR_PANEL = {
    spol: 'Gender', dizajner: 'Designers', 'ingredients.EN': 'Notes',
    godina: 'Year', votes: 'Minimum votes'
  };
  var UI_KINDS = ['refinementList', 'numericMenu', 'range', 'ratingMenu', 'menu', 'toggle'];

  function uiIndexState() {
    if (!search) return null;
    try {
      var ui = search.getUiState();
      return ui[search.mainIndex.getIndexId()] || null;
    } catch (e) { return null; }
  }

  function panelByTitle(panels, title) {
    for (var i = 0; i < panels.length; i++) if (panels[i].title === title) return panels[i];
    return null;
  }

  function learnAttrs(panels, st) {
    var rl = st.refinementList || {};
    Object.keys(rl).forEach(function (attr) {
      if (ATTR_PANEL[attr]) return;
      panels.forEach(function (p) {
        if (p.type !== 'list') return;
        p.items.forEach(function (it) { if (it.checked && rl[attr].indexOf(it.value) >= 0) ATTR_PANEL[attr] = p.title; });
      });
    });
    var nm = st.numericMenu || {};
    Object.keys(nm).forEach(function (attr) {
      if (ATTR_PANEL[attr]) return;
      var digits = String(nm[attr]).replace(/[^\d]/g, '');
      panels.forEach(function (p) {
        if (p.type !== 'radio') return;
        p.items.forEach(function (it, i) {
          if (i > 0 && it.checked && it.label.replace(/[^\d]/g, '') === digits) ATTR_PANEL[attr] = p.title;
        });
      });
    });
    Object.keys(st.ratingMenu || {}).forEach(function (attr) {
      if (!ATTR_PANEL[attr]) panels.forEach(function (p) { if (p.type === 'rating') ATTR_PANEL[attr] = p.title; });
    });
  }

  function refinementLabel(kind, attr, value, panels) {
    var title = ATTR_PANEL[attr] || attr;
    var p = panelByTitle(panels, title);
    if (kind === 'refinementList' || kind === 'menu') return String(value);
    if (kind === 'range') {
      var parts = String(value).split(':');
      var from = parts[0], to = parts[1];
      var r = from && to ? from + '–' + to : from ? 'from ' + from : 'until ' + to;
      return title + ' ' + r;
    }
    if (kind === 'ratingMenu') return 'Rating ' + value + ' & up';
    if (kind === 'numericMenu' && p) {
      var digits = String(value).replace(/[^\d]/g, '');
      for (var i = 1; i < p.items.length; i++) {
        if (p.items[i].label.replace(/[^\d]/g, '') === digits) return title + ': ' + p.items[i].label;
      }
    }
    if (kind === 'numericMenu') return title + ': ' + String(value).replace(/:$/, '+');
    return title + ': ' + value;
  }

  // nbHits with one attribute's refinements dropped. InstantSearch already
  // fetched these (one disjunctive query per refined facet), so no requests.
  function countsWithout() {
    var out = {};
    try {
      var raw = search.mainIndex.getResults()._rawResults || [];
      for (var i = 1; i < raw.length; i++) {
        var f = raw[i].facets ? Object.keys(raw[i].facets) : [];
        if (f.length === 1 && typeof raw[i].nbHits === 'number') out[f[0]] = raw[i].nbHits;
      }
    } catch (e) { /* unavailable */ }
    return out;
  }

  function readRefinements(panels) {
    var st = uiIndexState();
    if (!st) return null;
    learnAttrs(panels, st);
    var without = countsWithout();
    var out = [];
    UI_KINDS.forEach(function (kind) {
      var group = st[kind];
      if (!group) return;
      Object.keys(group).forEach(function (attr) {
        var vals = Array.isArray(group[attr]) ? group[attr] : [group[attr]];
        vals.forEach(function (v) {
          if (v === '' || v == null) return;
          // Only exact when it's the attribute's single value.
          var w = kind === 'refinementList' && vals.length === 1 && attr in without ? without[attr] : null;
          out.push({ kind: kind, attribute: attr, value: String(v), panel: ATTR_PANEL[attr] || attr, label: refinementLabel(kind, attr, v, panels), without: w });
        });
      });
    });
    return out;
  }

  function editUiState(fn) {
    if (!search) return false;
    var id = search.mainIndex.getIndexId();
    search.setUiState(function (all) {
      var next = {};
      for (var k in all) next[k] = all[k];
      var st = {};
      for (var j in (all[id] || {})) st[j] = all[id][j];
      fn(st);
      next[id] = st;
      return next;
    });
    return true;
  }

  // Remove one refinement. With results on screen, use the panel's own control.
  // At 0 results the site unmounts those controls and setUiState/popstate edits
  // drop every refinement, so rewrite the URL (values joined by "~") and load it.
  function removeRefinement(kind, attr, value) {
    var title = ATTR_PANEL[attr];
    if (title && total() > 0) {
      if (kind === 'range') return setRange(title, null, null);
      var p = panelByTitle(readFacets().panels, title);
      if (p) {
        var digits = value.replace(/[^\d]/g, '');
        for (var i = 0; i < p.items.length; i++) {
          var it = p.items[i];
          var hit = kind === 'numericMenu' ? i > 0 && it.label.replace(/[^\d]/g, '') === digits
            : kind === 'ratingMenu' ? it.checked
            : it.value === value || it.label === value;
          if (hit && applyFacet(title, it.value, false)) return true;
        }
      }
    }
    return removeViaUrl(value);
  }

  function removeViaUrl(value) {
    var u = new URL(location.href);
    var keys = [];
    u.searchParams.forEach(function (v, k) { keys.push(k); });
    for (var i = 0; i < keys.length; i++) {
      var k = keys[i];
      if (k === 'query') continue;
      var parts = (u.searchParams.get(k) || '').split('~');
      var at = parts.indexOf(value);
      if (at < 0) continue;
      parts.splice(at, 1);
      if (parts.length) u.searchParams.set(k, parts.join('~')); else u.searchParams.delete(k);
      var qs = u.searchParams.toString().replace(/\+/g, '%20');
      location.href = u.pathname + (qs ? '?' + qs : '');
      return true;
    }
    return false;
  }

  // ---------------------------------------------------------------- DOM writes
  function setValue(input, value) {
    var proto = Object.getPrototypeOf(input);
    var desc = Object.getOwnPropertyDescriptor(proto, 'value');
    if (desc && desc.set) desc.set.call(input, value); else input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function applyFacet(panel, value, on) {
    var el = sectionByTitle(panel);
    if (!el) return false;

    var ratings = el.querySelectorAll(SEL.ratingItem);
    if (ratings.length) {
      for (var r = 0; r < ratings.length; r++) {
        var a = ratings[r].querySelector(SEL.ratingLink);
        var label = a ? (a.getAttribute('aria-label') || text(a)) : text(ratings[r]);
        var sel = ratings[r].classList.contains(SEL.ratingSelected);
        if (label === value && sel !== !!on && a) { a.click(); return true; }
      }
      return false;
    }

    var lis = el.querySelectorAll(SEL.listItem);
    var radios = [];
    for (var i = 0; i < lis.length; i++) {
      var input = lis[i].querySelector('input[type="checkbox"], input[type="radio"]');
      if (!input) continue;
      if (input.type === 'radio') radios.push(input);
      var lbl = itemLabel(lis[i], input);
      if (input.value === value || lbl === value) {
        if (input.type === 'radio') {
          if (on && !input.checked) { input.click(); return true; }
          if (!on && input.checked && radios[0] !== input) {
            // Turning a radio off means going back to the first option ("All").
            var first = el.querySelector('input[type="radio"]');
            if (first) first.click();
            return true;
          }
          return false;
        }
        if (input.checked !== !!on) input.click();
        return true;
      }
    }
    return false;
  }

  // The site applies each year field ~0.5s after its own 'input' event, and a
  // second field set before the first lands overwrites it. So: one field at a
  // time, waiting for the URL to change (or a timeout) in between.
  var RANGE_SETTLE_MS = 1500;
  function setRange(panel, from, to) {
    var el = sectionByTitle(panel);
    if (!el || !el.querySelector(SEL.range)) return false;
    var vals = [from, to];
    var i = 0;
    (function next() {
      var ins = el.querySelectorAll(SEL.range + ' ' + SEL.rangeInputs);
      while (i < 2 && ins[i] && ins[i].value === (vals[i] == null ? '' : String(vals[i]))) i++;
      if (i >= 2 || !ins[i]) return;
      var input = ins[i++];
      var before = location.search;
      var desc = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
      desc.set.call(input, vals[i - 1] == null ? '' : String(vals[i - 1]));
      input.dispatchEvent(new Event('input', { bubbles: true }));
      var waited = 0;
      (function settle() {
        if (location.search !== before || waited >= RANGE_SETTLE_MS) { schedule(); next(); return; }
        waited += 100;
        setTimeout(settle, 100);
      })();
    })();
    return true;
  }

  function setQuery(q) {
    var input = document.querySelector(SEL.mainSearch);
    if (!input) return false;
    var current = ((uiIndexState() || {}).query) || input.value;
    if (current === q) return false;
    if (total() === 0) {
      // Same 0-result problem as removeRefinement: change it in the URL instead.
      var u = new URL(location.href);
      if (q) u.searchParams.set('query', q); else u.searchParams.delete('query');
      var qs = u.searchParams.toString().replace(/\+/g, '%20');
      location.href = u.pathname + (qs ? '?' + qs : '');
      return true;
    }
    setValue(input, q);
    return true;
  }

  function setSort(value) {
    var sel = document.querySelector(SEL.sortSelect);
    if (!sel || sel.value === value) return false;
    sel.value = value;
    sel.dispatchEvent(new Event('change', { bubbles: true }));
    return true;
  }

  function facetSearch(panel, q) {
    var el = sectionByTitle(panel);
    var input = el && el.querySelector(SEL.facetSearch);
    if (!input) return false;
    setValue(input, q);
    return true;
  }

  function showMore(panel, more) {
    var el = sectionByTitle(panel);
    var btn = el && el.querySelector(SEL.showMore);
    if (!btn || btn.disabled) return false;
    var expanded = /less/i.test(text(btn));
    if (expanded !== !!more) btn.click();
    return true;
  }

  // Next page of results: the site's own "Show more results" button.
  function loadMore() {
    var btns = document.querySelectorAll(SEL.showMoreHits);
    for (var i = 0; i < btns.length; i++) {
      if (SEL.showMoreHitsText.test(btns[i].textContent)) { btns[i].click(); return true; }
    }
    return false;
  }

  function clearAll() {
    if (editUiState(function (st) { UI_KINDS.forEach(function (k) { delete st[k]; }); })) return;
    var st = readFacets();
    for (var i = 0; i < st.panels.length; i++) {
      var p = st.panels[i];
      if (p.type === 'range') {
        if (p.range.from != null || p.range.to != null) setRange(p.title, null, null);
        continue;
      }
      for (var j = 0; j < p.items.length; j++) {
        var it = p.items[j];
        if (!it.checked) continue;
        if (p.type === 'radio' && j === 0) continue;
        applyFacet(p.title, it.value, false);
      }
    }
  }

  // ---------------------------------------------------------------- observe
  var timer = 0, lastJson = '';
  function schedule() {
    if (timer) return;
    timer = setTimeout(function () {
      timer = 0;
      var grid = document.querySelector(SEL.gridViewButton);
      if (grid) grid.click();
      if (!search) {
        search = findSearch();
        if (search) search.on('render', schedule);
      }
      var st = readFacets();
      var json = JSON.stringify(st);
      if (json !== lastJson) { lastJson = json; post(st); }
    }, 120);
  }

  ['pushState', 'replaceState'].forEach(function (m) {
    var orig = history[m];
    history[m] = function () {
      var r = orig.apply(this, arguments);
      schedule();
      return r;
    };
  });
  window.addEventListener('popstate', schedule);

  // ---------------------------------------------------------------- scroll restore
  // Leaving for a perfume page records how far the user got; coming Back
  // (a back_forward navigation) reloads that many results and scrolls there.
  var RESTORE_KEY = 'hyperfragz-restore';
  var RESTORE_MAX_PAGES = 10;

  function saveScroll() {
    try {
      sessionStorage.setItem(RESTORE_KEY, JSON.stringify({
        url: location.href, hits: document.querySelectorAll(SEL.hit).length, y: window.scrollY
      }));
    } catch (e) { /* storage unavailable */ }
  }

  function restoreScroll() {
    var nav = performance.getEntriesByType && performance.getEntriesByType('navigation')[0];
    if (!nav || nav.type !== 'back_forward') return;
    var saved;
    try { saved = JSON.parse(sessionStorage.getItem(RESTORE_KEY) || 'null'); } catch (e) { return; }
    if (!saved || saved.url !== location.href) return;
    var clicks = 0, waited = 0, sinceClick = 0, lastCount = -1;
    function done() { window.scrollTo(0, saved.y); }
    function findMore() {
      var btns = document.querySelectorAll(SEL.showMoreHits);
      for (var b = 0; b < btns.length; b++) if (SEL.showMoreHitsText.test(btns[b].textContent)) return btns[b];
      return null;
    }
    (function step() {
      var count = document.querySelectorAll(SEL.hit).length;
      // Wait for the first results and for the site's search to be live.
      if (count === 0 || total() == null) {
        if ((waited += 200) > 20000) return;
        return setTimeout(step, 200);
      }
      if (count >= saved.hits || clicks >= RESTORE_MAX_PAGES) return done();
      if (count !== lastCount || sinceClick >= 2500) {
        // New page arrived (or the last click did nothing): ask for the next one.
        var more = findMore();
        if (!more) {
          if ((waited += 200) > 4000) return done();
          return setTimeout(step, 200);
        }
        lastCount = count; sinceClick = 0; clicks++;
        more.click();
      } else {
        sinceClick += 200;
      }
      setTimeout(step, 200);
    })();
  }

  function start() {
    document.addEventListener('click', function (e) {
      if (e.target.closest && e.target.closest(SEL.hit)) saveScroll();
    }, true);
    restoreScroll();
    new MutationObserver(schedule).observe(document.body, {
      subtree: true, childList: true, attributes: true, characterData: true,
      attributeFilter: ['class', 'value', 'checked', 'aria-expanded']
    });
    // Checkbox .checked changes don't mutate attributes; listen too.
    document.addEventListener('change', schedule, true);
    schedule();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();

  function wrap(fn) {
    return function () {
      var r = fn.apply(null, arguments);
      schedule();
      return r;
    };
  }

  window.VitrineBridge = {
    readFacets: function () { return JSON.stringify(readFacets()); },
    applyFacet: wrap(applyFacet),
    setRange: wrap(setRange),
    setQuery: wrap(setQuery),
    setSort: wrap(setSort),
    facetSearch: wrap(facetSearch),
    showMore: wrap(showMore),
    clearAll: wrap(clearAll),
    removeRefinement: wrap(removeRefinement),
    loadMore: wrap(loadMore),
    saveScroll: saveScroll,
    refresh: function () { lastJson = ''; schedule(); }
  };
})();
