import type { ObjectDirective } from 'vue'

/**
 * `v-reveal` — fades an element in the first time it scrolls into view.
 * The binding value is an optional stagger delay in milliseconds.
 *
 * Shared by LandingPage.vue and PosMarketing.vue; the matching `.reveal`
 * classes live in marketing.css.
 */
export const vReveal: ObjectDirective<HTMLElement, number | undefined> = {
  mounted(el, binding) {
    el.classList.add('reveal')
    if (binding.value) el.style.transitionDelay = `${binding.value}ms`
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('reveal-visible')
            observer.unobserve(entry.target)
          }
        })
      },
      { threshold: 0.12 },
    )
    observer.observe(el)
  },
}
