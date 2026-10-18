/*
 * HyperFragz extractor. The WebView is hidden; this reads the loaded page and
 * hands JSON to the native UI (window.VitrineNative.onDetail).
 *
 * Every section is extracted on its own: a failure yields null for that
 * section only (native hides it). Selectors drift; patch X below.
 */
(function () {
  'use strict';
  if (window.HyperExtract || window.top !== window) return;

  var X = {
    hit: 'a.prefumeHbox',
    detailRoot: 'main .col-span-12 > div.p-2',
    title: '#toptop h1, h1[itemprop="name"]',
    brand: '[itemprop="brand"] [itemprop="name"]',
    image: 'img[itemprop="image"]',
    description: '#perfume-description-content',
    ratingValue: '[itemprop="ratingValue"]',
    ratingCount: '[itemprop="ratingCount"]',
    accordBar: '#toptop + div [class*="rounded-br"]',
    pyramidLevel: '#pyramid h4',
    pyramidNotes: '.pyramid-level-container a',
    ratingCard: '.tw-rating-card',
    ratingCardLabel: '.tw-rating-card-label',
    countColumn: '.flex-col.items-center',
    performance: '#performance',
    perfCard: '.tw-perf-card',
    perfRow: '.space-y-2 > div',
    noses: 'a[href*="/noses/"]',
    proItem: '[class*="group/item"]',
    similar: '#similar',
    carouselCard: '.tw-carousel-perfume-card',
    reviewPanel: '.review-tab-panel',
    reviewTab: '.tw-review-tab',
    reviewCard: '.tw-review-card',
    reviewName: 'meta[itemprop="name"]',
    reviewDate: '[itemprop="datePublished"]',
    reviewTag: '.justify-end span.rounded-full',
    reviewProse: '.tw-review-prose',
    helpfulBtn: 'button[aria-label="Helpful"]',
    loginModal: /join fragrantica/i
  };
  var VERDICTS = ['love', 'like', 'ok', 'dislike', 'hate'];

  // ---------------------------------------------------------------- eager sections
  // The site renders its lazy sections (and fetches their data) when they
  // scroll into view. Nobody sees this page, so tell every observer that its
  // targets are visible at once: all sections load in parallel, no scrolling.
  // Runs at document start, before the site's own scripts.
  if (/^\/perfume\//.test(location.pathname) && window.IntersectionObserver) {
    var NativeIO = window.IntersectionObserver;
    var AD_SLOT = '[id^="fragranticacom_"], .freeStarFragrantica, [data-fuse], [data-placement-name], [id^="div-gpt"], ins.adsbygoogle';
    var EagerIO = function (cb, opts) {
      var io = new NativeIO(cb, opts), observe = io.observe.bind(io);
      io.observe = function (el) {
        observe(el);
        if (el.matches && el.matches(AD_SLOT)) return; // ads keep their real visibility
        setTimeout(function () {
          var r = el.getBoundingClientRect();
          try {
            cb([{ target: el, isIntersecting: true, intersectionRatio: 1, time: performance.now(),
              boundingClientRect: r, intersectionRect: r, rootBounds: null }], io);
          } catch (e) { /* the site's own callback */ }
        }, 0);
      };
      return io;
    };
    EagerIO.prototype = NativeIO.prototype;
    window.IntersectionObserver = EagerIO;
  }
  // The rest hydrate "when the browser is idle" (Vue hydrateOnIdle, up to a
  // 10s timeout), which on a busy page is seconds late. Don't wait for idle.
  if (/^\/perfume\//.test(location.pathname)) {
    window.requestIdleCallback = function (cb) {
      return setTimeout(function () { cb({ didTimeout: false, timeRemaining: function () { return 50; } }); }, 0);
    };
    window.cancelIdleCallback = function (id) { clearTimeout(id); };
  }

  // ---------------------------------------------------------------- helpers
  function text(el) { return el ? (el.textContent || '').replace(/\s+/g, ' ').trim() : ''; }
  function count(s) {
    var m = String(s || '').replace(/,/g, '').match(/([\d.]+)\s*([kKmM]?)/);
    if (!m) return null;
    var n = parseFloat(m[1]);
    if (/k/i.test(m[2])) n *= 1e3;
    if (/m/i.test(m[2])) n *= 1e6;
    return Math.round(n);
  }
  function abs(href) { try { return new URL(href, location.href).href; } catch (e) { return null; } }
  // Prefer the WebP source (AVIF needs Android 12+; minSdk is 26), which on
  // this site is also the dark-background bottle shot.
  function imgSrc(root) {
    if (!root) return null;
    var webp = root.querySelector('source[type="image/webp"]');
    var set = webp && (webp.getAttribute('srcset') || '');
    if (set) {
      var parts = set.split(','), best = null;
      for (var i = 0; i < parts.length; i++) {
        var p = parts[i].trim().split(/\s+/);
        if (!best || /2x/.test(p[1] || '')) best = p[0];
      }
      if (best) return abs(best);
    }
    var img = root.querySelector('img');
    return img ? abs(img.getAttribute('src') || img.getAttribute('data-src')) : null;
  }
  function safe(fn) { try { var v = fn(); return v == null ? null : v; } catch (e) { return null; } }
  function heading(re) {
    var hs = document.querySelectorAll('h2, h3');
    for (var i = 0; i < hs.length; i++) if (re.test(text(hs[i]))) return hs[i];
    return null;
  }
  function sectionOf(h) {
    // Climb to the direct child of the detail root.
    var root = document.querySelector(X.detailRoot);
    var el = h;
    while (el && el.parentElement && el.parentElement !== root) el = el.parentElement;
    return el;
  }
  function post(kind, data) {
    try { if (window.VitrineNative) window.VitrineNative.onDetail(JSON.stringify({ kind: kind, data: data })); } catch (e) { /* gone */ }
  }

  // ---------------------------------------------------------------- search hits
  function hits() {
    var out = [];
    var cards = document.querySelectorAll(X.hit);
    for (var i = 0; i < cards.length; i++) {
      var a = cards[i];
      // aria-label: "Link to <Brand> <Name> <gender> <year>"
      var label = a.getAttribute('aria-label') || a.getAttribute('title') || '';
      var year = (label.match(/(\d{4})\s*$/) || [])[1] || null;
      var gender = (label.match(/\b(female|male|unisex)\b(?=\s*\d{0,4}\s*$)/i) || [])[1] || null;
      out.push({
        url: abs(a.getAttribute('href')),
        name: text(a.querySelector('h3')),
        brand: text(a.querySelector('h3 + p')),
        year: year,
        gender: gender,
        thumb: imgSrc(a)
      });
    }
    return out;
  }

  // ---------------------------------------------------------------- detail sections
  function identity() {
    var h1 = document.querySelector(X.title);
    var brand = text(document.querySelector(X.brand));
    var name = '';
    if (h1) for (var n = h1.firstChild; n; n = n.nextSibling) if (n.nodeType === 3) name += n.textContent;
    name = name.replace(/\s+/g, ' ').trim();
    if (brand && name.slice(-brand.length) === brand) name = name.slice(0, -brand.length).trim();
    var genderSpan = h1 && h1.querySelector('span');
    var img = document.querySelector(X.image);
    return {
      name: name,
      brand: brand,
      genderLine: text(genderSpan),
      image: img ? imgSrc(img.closest('picture') || img.parentElement) : null
    };
  }

  function about() {
    var d = text(document.querySelector(X.description));
    if (!d) return null;
    var family = (d.match(/ is an? (.+?) fragrance for (women and men|women|men)/i) || []);
    var year = (d.match(/launched in (\d{4})/i) || [])[1] || null;
    var created = (d.match(/was created by (.+?)\.(\s|$)/i) || [])[1] || null;
    return { family: family[1] || null, audience: family[2] || null, year: year, createdBy: created };
  }

  function perfumers() {
    var a = about();
    if (a && a.createdBy) return a.createdBy;
    var links = document.querySelectorAll(X.noses), names = [];
    for (var i = 0; i < links.length; i++) {
      var href = links[i].getAttribute('href') || '';
      var t = text(links[i]);
      if (/\/noses\/.+\.html/.test(href) && t && names.indexOf(t) < 0) names.push(t);
    }
    if (!names.length) return null;
    return names.length === 1 ? names[0] : names.slice(0, -1).join(', ') + ' and ' + names[names.length - 1];
  }

  function rating() {
    var v = document.querySelector(X.ratingValue), c = document.querySelector(X.ratingCount);
    var value = v ? parseFloat(v.getAttribute('content') || text(v)) : null;
    var votes = c ? count(c.getAttribute('content') || text(c)) : null;
    if (value == null || isNaN(value)) return null;
    return { value: value, votes: votes, breakdown: countCard(/^rating$/i) };
  }

  // A tw-rating-card whose header matches → [{label, count}]
  function countCard(re) {
    var cards = document.querySelectorAll(X.ratingCard);
    for (var i = 0; i < cards.length; i++) {
      if (!re.test(text(cards[i].querySelector(X.ratingCardLabel)))) continue;
      var cols = cards[i].querySelectorAll(X.countColumn), out = [];
      for (var j = 0; j < cols.length; j++) {
        var spans = cols[j].querySelectorAll('span');
        if (spans.length < 2) continue;
        out.push({ label: text(spans[0]), count: count(text(spans[spans.length - 1])) || 0 });
      }
      return out.length ? out : null;
    }
    return null;
  }

  function accords() {
    var bars = document.querySelectorAll(X.accordBar), out = [];
    for (var i = 0; i < bars.length; i++) {
      var w = parseFloat(((bars[i].getAttribute('style') || '').match(/width:\s*([\d.]+)%/) || [])[1]);
      out.push({ label: text(bars[i]), width: isNaN(w) ? null : w });
    }
    return out.length ? out : null;
  }

  function notes() {
    var levels = document.querySelectorAll(X.pyramidLevel), out = {};
    for (var i = 0; i < levels.length; i++) {
      var lvl = text(levels[i]).toLowerCase();
      var key = /top/.test(lvl) ? 'top' : /middle|heart/.test(lvl) ? 'middle' : /base/.test(lvl) ? 'base' : null;
      if (!key) continue;
      var box = levels[i].closest('.max-w-md') || levels[i].parentElement.parentElement;
      var links = box ? box.querySelectorAll(X.pyramidNotes) : [], names = [];
      for (var j = 0; j < links.length; j++) {
        var t = (links[j].innerText || '').replace(/\s+/g, ' ').trim();
        if (t && names.indexOf(t) < 0) names.push(t);
      }
      if (names.length) out[key] = names;
    }
    if (!out.top && !out.middle && !out.base) {
      // Flat note lists (no pyramid): everything under #pyramid.
      var all = document.querySelectorAll('#pyramid ' + X.pyramidNotes), flat = [];
      for (var k = 0; k < all.length; k++) { var n = (all[k].innerText || '').trim(); if (n && flat.indexOf(n) < 0) flat.push(n); }
      return flat.length ? { flat: flat } : null;
    }
    return out;
  }

  function perfCounts(re) {
    var sec = document.querySelector(X.performance);
    if (!sec) return null;
    var cards = sec.querySelectorAll(X.perfCard);
    for (var i = 0; i < cards.length; i++) {
      if (!re.test(text(cards[i]).slice(0, 40))) continue;
      var rows = cards[i].querySelectorAll(X.perfRow), out = [];
      for (var j = 0; j < rows.length; j++) {
        var spans = rows[j].querySelectorAll('span');
        if (spans.length < 2) continue;
        out.push({ label: text(spans[0]), count: count(text(spans[1])) || 0 });
      }
      return out.length ? out : null;
    }
    return null;
  }

  function prosCons() {
    var h = heading(/what people say/i);
    if (!h) return null;
    var sec = sectionOf(h);
    var out = { pros: [], cons: [], disclaimer: null };
    var items = sec.querySelectorAll(X.proItem);
    for (var i = 0; i < items.length; i++) {
      var card = items[i].closest('.p-3') || items[i].parentElement.parentElement;
      var kind = /cons/i.test(text(card).slice(0, 12)) ? 'cons' : 'pros';
      var btns = items[i].querySelectorAll('button');
      if (btns.length < 2) continue;
      out[kind].push({
        text: text(items[i].querySelector('p')),
        agree: count(text(btns[0])) || 0,
        disagree: count(text(btns[1])) || 0
      });
    }
    var ps = sec.querySelectorAll('p');
    for (var k = 0; k < ps.length; k++) if (/AI-generated/i.test(text(ps[k]))) out.disclaimer = text(ps[k]).replace(/^Note:\s*/i, '');
    return out.pros.length || out.cons.length ? out : null;
  }

  function cards(sec, withVotes) {
    if (!sec) return null;
    var cs = sec.querySelectorAll(X.carouselCard), out = [];
    for (var i = 0; i < cs.length; i++) {
      var c = cs[i];
      var a = c.querySelector('a[href*="/perfume/"]');
      var ps = c.querySelectorAll('p');
      var item = {
        url: a ? abs(a.getAttribute('href')) : null,
        brand: text(ps[0]),
        name: text(ps[1]),
        image: imgSrc(c)
      };
      if (withVotes) {
        var v = c.querySelectorAll('.cursor-pointer');
        item.yes = v[0] ? count(text(v[0])) || 0 : null;
        item.no = v[1] ? count(text(v[1])) || 0 : null;
      }
      if (item.url && item.name) out.push(item);
    }
    return out.length ? out : null;
  }

  function reviews() {
    var panel = null, panels = document.querySelectorAll(X.reviewPanel);
    for (var i = 0; i < panels.length; i++) if (!panels[i].classList.contains('hidden')) { panel = panels[i]; break; }
    var totalLink = null, links = document.querySelectorAll('a');
    for (var l = 0; l < links.length; l++) if (/^\s*Reviews\s*\(/.test(links[l].textContent)) { totalLink = links[l]; break; }
    var tabs = document.querySelectorAll(X.reviewTab), active = null;
    for (var t = 0; t < tabs.length; t++) if (/active/.test(tabs[t].className) && !/inactive/.test(tabs[t].className)) active = text(tabs[t]);
    var out = { total: totalLink ? count((totalLink.textContent.match(/\(([^)]+)\)/) || [])[1]) : null, sort: sortKey(active), items: [] };
    if (!panel) return out.total ? out : null;
    var cs = panel.querySelectorAll(X.reviewCard);
    for (var j = 0; j < cs.length && j < 60; j++) {
      var c = cs[j];
      var name = c.querySelector(X.reviewName);
      var date = c.querySelector(X.reviewDate);
      var dateText = date && date.previousElementSibling ? text(date.previousElementSibling) : '';
      var tags = c.querySelectorAll(X.reviewTag), verdict = null, tagList = [];
      for (var k = 0; k < tags.length; k++) {
        var tv = text(tags[k]).toLowerCase();
        if (!verdict && VERDICTS.indexOf(tv) >= 0) { verdict = tv; continue; }
        tagList.push(tagLabel(tags[k]));
      }
      var prose = c.querySelector(X.reviewProse);
      out.items.push({
        id: (c.id || '').replace(/^parent/, ''),
        user: name ? name.getAttribute('content') : '',
        date: dateText || (date ? date.getAttribute('content') : ''),
        verdict: verdict,
        tags: tagList,
        text: prose ? (prose.innerText || '').replace(/\n{3,}/g, '\n\n').trim() : ''
      });
    }
    return out;
  }
  // Review vote tags are told apart by the site's colour classes:
  // amber = longevity, purple = sillage; seasons/day-night carry a title.
  function tagLabel(tag) {
    var cls = tag.className || '';
    var title = tag.getAttribute('title');
    var t = text(tag);
    if (title) {
      var parts = title.split(/\s*·\s*/);
      if (parts.length === 2 && /day/.test(parts[0]) && /night/.test(parts[1])) return 'Day and night';
      return parts.join(', ');
    }
    if (/amber/.test(cls) && !/lasting|eternal/i.test(t)) return t + ' longevity';
    if (/purple/.test(cls)) return t + ' sillage';
    return t;
  }

  function sortKey(label) {
    if (/positive/i.test(label || '')) return 'positive';
    if (/negative/i.test(label || '')) return 'negative';
    return 'newest';
  }

  function detail() {
    return {
      url: location.href,
      identity: safe(identity),
      about: safe(about),
      perfumers: safe(perfumers),
      rating: safe(rating),
      accords: safe(accords),
      notes: safe(notes),
      longevity: safe(function () { return perfCounts(/longevity/i); }),
      sillage: safe(function () { return perfCounts(/sillage/i); }),
      wear: safe(function () { return countCard(/when to wear/i); }),
      prosCons: safe(prosCons),
      similar: safe(function () { return cards(document.querySelector(X.similar), true); }),
      alsoLike: safe(function () { var h = heading(/also like/i); return h ? cards(sectionOf(h), false) : null; }),
      reviews: safe(reviews)
    };
  }

  // ---------------------------------------------------------------- lazy loading
  // Lazy sections render when scrolled into view and pros/cons show 3 until
  // "Show more". Step the hidden page down once, expand pros/cons, then report.
  function expandProsCons() {
    var h = heading(/what people say/i);
    if (!h) return;
    var btns = sectionOf(h).querySelectorAll('button');
    for (var i = 0; i < btns.length; i++) {
      if (!btns[i].closest(X.proItem) && /show more/i.test(text(btns[i]))) btns[i].click();
    }
  }

  var running = false;
  function ready() { return !!document.querySelector(X.reviewCard) && pendingSections() === 0; }
  // Inside the rendered sections, these two widgets load their own code and
  // come a beat later; give them a moment so their placeholders don't vanish first.
  function slowReady() {
    return (!document.querySelector(X.performance) || !!perfCounts(/longevity/i)) && !!countCard(/when to wear/i);
  }

  // Sections normally all render on their own (see eager sections above);
  // scrolling the page is only the fallback if they haven't after a while.
  var EAGER_WAIT_MS = 2500, SLOW_WAIT_MS = 2000;
  function loadAll(done) {
    if (running) return;
    running = true;
    var start = Date.now();
    function finish() {
      expandProsCons();
      var t = Date.now();
      (function slow() {
        if (!slowReady() && Date.now() - t < SLOW_WAIT_MS) return setTimeout(slow, 150);
        window.scrollTo(0, 0);
        running = false;
        done();
      })();
    }
    (function wait() {
      if (ready()) return finish();
      if (Date.now() - start < EAGER_WAIT_MS) return setTimeout(wait, 150);
      var y = 0, step = Math.max(400, window.innerHeight * 0.8);
      (function down() {
        y += step;
        window.scrollTo(0, y);
        var bottom = y >= document.documentElement.scrollHeight - window.innerHeight;
        if (!ready() && !bottom && Date.now() - start < 10000) return setTimeout(down, 120);
        var waited = 0;
        (function settle() {
          if (!ready() && (waited += 300) < 4000) return setTimeout(settle, 300);
          finish();
        })();
      })();
    })();
  }

  // Lazy sections are empty <div><!----></div> wrappers until rendered.
  function pendingSections() {
    var root = document.querySelector(X.detailRoot);
    if (!root) return 0;
    var n = 0, kids = root.children;
    for (var i = 0; i < kids.length; i++) {
      var c = kids[i];
      if (!c.firstElementChild && !c.id && !c.className && !text(c)) n++;
    }
    return n;
  }

  function report(final) { post(final ? 'detail' : 'detail-partial', detail()); }

  // What's present, cheaply: re-post only when this changes.
  function signature(d) {
    var out = [];
    for (var k in d) {
      var v = d[k];
      if (v == null || k === 'url') continue;
      out.push(k + ':' + (Array.isArray(v) ? v.length : k === 'reviews' ? (v.items || []).length + v.sort
        : k === 'rating' ? !!v.breakdown : k === 'prosCons' ? v.pros.length + '/' + v.cons.length : 1));
    }
    return out.join('|');
  }

  // Sections stream to native as soon as the site renders them (lazy ones
  // appear while loadAll scrolls), then one final report.
  // After the final report, keep watching a while: a slow section that
  // renders late is sent as a further final update.
  var WATCH_AFTER_FINAL_MS = 10000;

  function startDetail() {
    var last = '', finalAt = 0;
    (function stream() {
      if (finalAt && Date.now() - finalAt > WATCH_AFTER_FINAL_MS) return;
      var d = detail(), sig = signature(d);
      if (sig !== last) { last = sig; post(finalAt ? 'detail' : 'detail-partial', d); }
      setTimeout(stream, finalAt ? 1000 : 350); // slower once the final report is out
    })();
    setTimeout(function () {
      loadAll(function () {
        var d = detail();
        last = signature(d);
        finalAt = Date.now();
        post('detail', d);
      });
    }, 100);
  }

  // ---------------------------------------------------------------- detail actions
  function sortReviews(kind) {
    var tabs = document.querySelectorAll(X.reviewTab);
    var re = kind === 'positive' ? /positive/i : kind === 'negative' ? /negative/i : /by date/i;
    for (var i = 0; i < tabs.length; i++) if (re.test(text(tabs[i]))) { tabs[i].click(); break; }
    var waited = 0;
    (function poll() {
      var r = reviews();
      if ((r && r.sort === kind && r.items.length) || (waited += 300) > 6000) return post('detail', detail());
      setTimeout(poll, 300);
    })();
  }

  // Proxies the site's own Helpful action. Guests get the site's login prompt,
  // which is invisible in the hidden WebView, so report it and close it.
  function helpful(id) {
    var card = document.getElementById('parent' + id);
    var btn = card && card.querySelector(X.helpfulBtn);
    if (!btn) return post('helpful', { id: id, ok: false });
    btn.click();
    setTimeout(function () {
      var modal = null, all = document.querySelectorAll('body *');
      for (var i = 0; i < all.length && !modal; i++) {
        var s = getComputedStyle(all[i]);
        if (s.position === 'fixed' && all[i].offsetHeight > 200 && X.loginModal.test(all[i].innerText || '')) modal = all[i];
      }
      if (modal) {
        var close = modal.querySelector('button[aria-label*="lose" i], button');
        if (close) close.click();
        return post('helpful', { id: id, ok: false, login: true });
      }
      post('helpful', { id: id, ok: true, pressed: btn.getAttribute('aria-pressed') === 'true' });
    }, 600);
  }

  window.HyperExtract = {
    hits: hits,
    detail: function () { return JSON.stringify(detail()); },
    sortReviews: sortReviews,
    helpful: helpful,
    refresh: function () { report(true); }
  };

  // The full HTML (with every review) can take a while to parse, so post a
  // first pass as soon as the title and description exist, then the real
  // passes from DOMContentLoaded on.
  function early() {
    if (document.readyState !== 'loading') return;
    if (document.querySelector(X.description) && text(document.querySelector(X.title))) return report(false);
    setTimeout(early, 300);
  }

  if (/^\/perfume\//.test(location.pathname)) {
    if (document.readyState === 'loading') {
      setTimeout(early, 300);
      document.addEventListener('DOMContentLoaded', startDetail);
    } else startDetail();
  }
})();
