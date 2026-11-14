/* ---------------------------------------------------------------------------
   Custom elements.

   The site is server-rendered HTML and stays fully readable, navigable and
   usable with this file blocked — nothing here creates content. Each element
   below is either pure decoration (<foh-spectrum>) or an enhancement layered
   onto markup that already works without it (<foh-copy>, <foh-reveal>).

   That is the whole of the site's JavaScript: no dependencies, no build, no
   network calls, no cookies, no analytics.
   --------------------------------------------------------------------------- */

(() => {
	"use strict";

	const REDUCED = window.matchMedia("(prefers-reduced-motion: reduce)");

	/* =======================================================================
	   <foh-spectrum> — the animated analyser bars behind the hero.

	   A log-spaced band display in the app's own colours, driven by smoothed
	   pink-ish noise rather than real audio: it is an illustration of the
	   product, not a working analyser, and says so via aria-hidden.

	   Attributes
	     bars   number of bands            (default 48)
	     height CSS height of the canvas   (default 180)

	   It suspends when scrolled out of view or when the tab is hidden, and
	   with prefers-reduced-motion it paints exactly one static frame.
	   ======================================================================= */

	class FohSpectrum extends HTMLElement {
		connectedCallback() {
			if (this.dataset.ready) return;
			this.dataset.ready = "1";

			// Decorative: keep it out of the accessibility tree entirely.
			this.setAttribute("aria-hidden", "true");

			this.bars = Math.max(8, Number(this.getAttribute("bars")) || 48);
			// The canvas is styled in components.css; only its height is an
			// argument, handed over as a custom property.
			this.style.setProperty("--spectrum-h", (Number(this.getAttribute("height")) || 180) + "px");
			this.canvas = document.createElement("canvas");
			this.appendChild(this.canvas);
			this.ctx = this.canvas.getContext("2d");

			// Per-band state: current level, the falling peak-hold cap, and a
			// per-band phase so neighbours do not move in lockstep.
			this.level = new Float32Array(this.bars);
			this.peak = new Float32Array(this.bars);
			this.phase = new Float32Array(this.bars);
			for (let i = 0; i < this.bars; i++) {
				this.phase[i] = i * 0.7;
				this.level[i] = this.target(i, 0);
				this.peak[i] = this.level[i];
			}

			this.resize = this.resize.bind(this);
			this.frame = this.frame.bind(this);
			this.t = 0;
			this.running = false;

			this.ro = new ResizeObserver(this.resize);
			this.ro.observe(this);
			this.resize();

			if (REDUCED.matches) {
				this.draw();
				return;
			}

			// Only animate while on screen and while the tab is in front.
			this.io = new IntersectionObserver((entries) => {
				this.onScreen = entries[0].isIntersecting;
				this.sync();
			});
			this.io.observe(this);
			this.onVis = () => this.sync();
			document.addEventListener("visibilitychange", this.onVis);
		}

		disconnectedCallback() {
			this.stop();
			this.ro?.disconnect();
			this.io?.disconnect();
			document.removeEventListener("visibilitychange", this.onVis);
		}

		sync() {
			const wanted = this.onScreen && !document.hidden;
			if (wanted) this.start();
			else this.stop();
		}

		start() {
			if (this.running) return;
			this.running = true;
			this.raf = requestAnimationFrame(this.frame);
		}

		stop() {
			this.running = false;
			if (this.raf) cancelAnimationFrame(this.raf);
		}

		resize() {
			const dpr = Math.min(window.devicePixelRatio || 1, 2);
			const rect = this.canvas.getBoundingClientRect();
			if (!rect.width || !rect.height) return;
			this.canvas.width = Math.round(rect.width * dpr);
			this.canvas.height = Math.round(rect.height * dpr);
			this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
			this.w = rect.width;
			this.h = rect.height;
			this.draw();
		}

		/** The level a band is heading toward: a broad tilt plus slow wobble. */
		target(i, t) {
			const x = i / (this.bars - 1);
			// A gentle downward tilt with a lift in the low-mids — the shape a
			// room actually measures, rather than a flat line.
			const tilt = 0.92 - 0.55 * Math.pow(x, 1.35) + 0.1 * Math.exp(-Math.pow((x - 0.22) / 0.16, 2));
			const wob =
				0.12 * Math.sin(t * 1.7 + this.phase[i]) +
				0.07 * Math.sin(t * 3.1 + this.phase[i] * 1.9) +
				0.05 * Math.sin(t * 0.6 + this.phase[i] * 0.4);
			return Math.max(0.04, Math.min(1, tilt + wob));
		}

		frame(now) {
			if (!this.running) return;
			this.t = now / 1000;
			for (let i = 0; i < this.bars; i++) {
				const want = this.target(i, this.t);
				// Fast attack, slow release — how a real meter behaves.
				const k = want > this.level[i] ? 0.28 : 0.09;
				this.level[i] += (want - this.level[i]) * k;
				this.peak[i] = Math.max(this.level[i], this.peak[i] - 0.0035);
			}
			this.draw();
			this.raf = requestAnimationFrame(this.frame);
		}

		draw() {
			const { ctx, w, h } = this;
			if (!ctx || !w) return;
			ctx.clearRect(0, 0, w, h);

			const gap = Math.max(1, w / this.bars / 5);
			const bw = w / this.bars - gap;

			for (let i = 0; i < this.bars; i++) {
				const x = i * (bw + gap);
				const bh = Math.max(2, this.level[i] * h);
				const y = h - bh;

				// Cyan through lime to amber across the spectrum, matching the
				// hero's gradient and the app's trace colours.
				const p = i / (this.bars - 1);
				const g = ctx.createLinearGradient(0, y, 0, h);
				const hue = p < 0.55 ? mix([34, 211, 238], [163, 230, 53], p / 0.55)
					: mix([163, 230, 53], [245, 165, 36], (p - 0.55) / 0.45);
				g.addColorStop(0, `rgba(${hue},0.85)`);
				g.addColorStop(1, `rgba(${hue},0.12)`);
				ctx.fillStyle = g;
				ctx.fillRect(x, y, bw, bh);

				// The peak-hold cap, as the app draws it.
				const py = h - Math.max(2, this.peak[i] * h);
				ctx.fillStyle = `rgba(${hue},0.5)`;
				ctx.fillRect(x, py, bw, 1.5);
			}
		}
	}

	function mix(a, b, t) {
		t = Math.max(0, Math.min(1, t));
		return a.map((v, i) => Math.round(v + (b[i] - v) * t)).join(",");
	}

	/* =======================================================================
	   <foh-copy> — wraps a <pre> and adds a copy-to-clipboard button.

	   The element renders nothing of its own; without JS, or without a secure
	   context for the clipboard API, the <pre> inside is simply a code block
	   that can be selected by hand.
	   ======================================================================= */

	class FohCopy extends HTMLElement {
		connectedCallback() {
			if (this.dataset.ready) return;
			const pre = this.querySelector("pre");
			// navigator.clipboard is undefined on plain http, so check before
			// offering a button that could not work.
			if (!pre || !navigator.clipboard) return;
			this.dataset.ready = "1";

			this.classList.add("codeblock");

			const btn = document.createElement("button");
			btn.type = "button";
			btn.className = "btn btn--mini codeblock__copy";
			btn.textContent = "copy";
			btn.setAttribute("aria-label", "Copy code to clipboard");
			this.appendChild(btn);

			btn.addEventListener("click", async () => {
				// The prompt marker is presentational; never copy it.
				const text = [...pre.childNodes]
					.map((n) => (n.classList?.contains("prompt") ? "" : n.textContent))
					.join("")
					.trim();
				try {
					await navigator.clipboard.writeText(text);
					this.flash(btn, "copied", "--lime");
				} catch {
					this.flash(btn, "press ⌘C", "--amber");
				}
			});
		}

		flash(btn, label, token) {
			clearTimeout(this.timer);
			btn.textContent = label;
			btn.style.color = `var(${token})`;
			this.timer = setTimeout(() => {
				btn.textContent = "copy";
				btn.style.color = "";
			}, 1600);
		}
	}

	/* =======================================================================
	   <foh-reveal> — fades its contents in as they scroll into view.

	   Purely visual. The element marks itself "pending" only once the script
	   is running, so content is never hidden by CSS that JS then fails to
	   un-hide; with reduced motion it does nothing at all.
	   ======================================================================= */

	class FohReveal extends HTMLElement {
		connectedCallback() {
			if (this.dataset.reveal || REDUCED.matches || !("IntersectionObserver" in window)) return;

			this.dataset.reveal = "pending";
			// A small stagger when several siblings enter together.
			const delay = Number(this.getAttribute("delay")) || 0;

			const io = new IntersectionObserver(
				(entries) => {
					for (const e of entries) {
						if (!e.isIntersecting) continue;
						setTimeout(() => (this.dataset.reveal = "shown"), delay);
						io.disconnect();
					}
				},
				{ rootMargin: "0px 0px -8% 0px", threshold: 0.05 }
			);
			io.observe(this);
		}
	}

	/* =======================================================================
	   Registration.

	   The elements' display:block comes from components.css, not from here:
	   an unknown element defaults to display:inline, so injecting that rule
	   from script would leave the page briefly mislaid before this file runs,
	   and permanently so if it never does.
	   ======================================================================= */

	customElements.define("foh-spectrum", FohSpectrum);
	customElements.define("foh-copy", FohCopy);
	customElements.define("foh-reveal", FohReveal);
})();
