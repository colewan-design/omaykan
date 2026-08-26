<script setup lang="ts">
import { computed, ref } from 'vue'
import { Eye, EyeOff, MailCheck, ShieldCheck } from '@lucide/vue'
import { messageFor, useCustomerAccount } from '@pos/web/commerce/customer'

/*
 * The account gate that stands between a full cart and a placed order.
 *
 * Filling a cart stays completely open — nobody is asked who they are while
 * they are still deciding. The gate is here, at the point where an order is
 * about to be created and a real person has to be reachable about it.
 *
 * Three states, one card, because they are one question:
 *
 *   signin   — the way through for anyone who already has an account
 *   register — creates one; deliberately does NOT sign them in
 *   sent     — "check your email", with the sign-in form kept right there
 *
 * `register` does not return a session on purpose: the API refuses to mint a
 * token for an address nobody has proved they hold (CustomerAuthController),
 * so a new shopper verifies first and signs in second. That round trip is why
 * `sent` keeps the sign-in fields on screen rather than sending them away —
 * they open the link in another tab, come back to this one, and their cart is
 * exactly where they left it. Nothing here touches the cart.
 */

const account = useCustomerAccount()

type Mode = 'signin' | 'register' | 'sent'

const mode = ref<Mode>('signin')
const pending = ref(false)
const error = ref('')

const form = ref({ name: '', email: '', phone: '', password: '' })
const showPassword = ref(false)

/** The address the link went to, held so `sent` can name it back. */
const sentTo = ref('')

const copy = computed(() => {
  switch (mode.value) {
    case 'register':
      return {
        title: 'Create your account',
        sub: 'Your order needs an account so the shop can reach you about it, and so you can follow it afterwards.',
        submit: 'Create account',
      }
    case 'sent':
      return {
        title: 'Check your email',
        sub: `We sent a verification link to ${sentTo.value}. Open it, then sign in below — your cart is waiting.`,
        submit: 'Sign in',
      }
    default:
      return {
        title: 'Sign in to check out',
        sub: 'Your cart is saved. Sign in to place the order, or create an account if this is your first time.',
        submit: 'Sign in and continue',
      }
  }
})

/** Local checks only spare an obvious round trip; the API validates it all. */
function localProblem(): string {
  const { name, email, password } = form.value

  if (!email.trim().includes('@')) return "That doesn't look like an email address."
  if (mode.value === 'register' && !name.trim()) return 'We need a name to put on your order.'
  if (!password) return 'Please enter your password.'
  // Matches PasswordRule::min(8) on the API — a shorter one would only bounce.
  if (mode.value === 'register' && password.length < 8) {
    return 'Passwords need at least 8 characters.'
  }

  return ''
}

function switchTo(next: Mode) {
  mode.value = next
  error.value = ''
  form.value.password = ''
  showPassword.value = false
}

async function submit() {
  if (pending.value) return

  const problem = localProblem()
  if (problem) {
    error.value = problem
    return
  }

  error.value = ''
  pending.value = true

  const { name, email, phone, password } = form.value

  try {
    if (mode.value === 'register') {
      await account.register({
        name: name.trim(),
        email: email.trim(),
        phone: phone.trim() || undefined,
        password,
      })

      sentTo.value = email.trim()
      mode.value = 'sent'
      form.value.password = ''
    } else {
      // Signing in flips `signedIn` on the module-level store, and the parent
      // swaps this card out for the order form. No emit needed.
      await account.signIn(email.trim(), password)
    }
  } catch (caught) {
    error.value = messageFor(caught)
    // Never left in the field after a failure: on a shared screen the next
    // person shouldn't be able to reveal it, and a wrong one is retyped anyway.
    form.value.password = ''
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section class="fdga">
    <div class="fdga__head">
      <div class="fdga__badge">
        <component :is="mode === 'sent' ? MailCheck : ShieldCheck" :size="20" :stroke-width="1.7" />
      </div>
      <div>
        <h2 class="fdga__title">{{ copy.title }}</h2>
        <p class="fdga__sub">{{ copy.sub }}</p>
      </div>
    </div>

    <form class="fdga__form" novalidate @submit.prevent="submit">
      <label v-if="mode === 'register'" class="fdga__field">
        <span>Your name</span>
        <input v-model="form.name" type="text" autocomplete="name" :disabled="pending" />
      </label>

      <label class="fdga__field">
        <span>Email</span>
        <input
          v-model="form.email"
          type="email"
          autocomplete="email"
          inputmode="email"
          :disabled="pending"
        />
      </label>

      <label v-if="mode === 'register'" class="fdga__field">
        <span>Mobile number <em>optional</em></span>
        <input v-model="form.phone" type="tel" autocomplete="tel" inputmode="tel" :disabled="pending" />
        <small>How a rider reaches you if they can't find the door.</small>
      </label>

      <label class="fdga__field">
        <span>Password</span>
        <span class="fdga__secret">
          <input
            v-model="form.password"
            :type="showPassword ? 'text' : 'password'"
            :autocomplete="mode === 'register' ? 'new-password' : 'current-password'"
            :disabled="pending"
          />
          <button
            type="button"
            class="fdga__peek"
            :aria-label="showPassword ? 'Hide password' : 'Show password'"
            :aria-pressed="showPassword"
            @click="showPassword = !showPassword"
          >
            <component :is="showPassword ? EyeOff : Eye" :size="17" :stroke-width="1.6" />
          </button>
        </span>
        <small v-if="mode === 'register'">At least 8 characters.</small>
      </label>

      <p v-if="error" class="fdga__alert" role="alert">{{ error }}</p>

      <button type="submit" class="fdga__go" :disabled="pending">
        {{ pending ? 'One moment…' : copy.submit }}
      </button>
    </form>

    <p class="fdga__switch">
      <template v-if="mode === 'register'">
        Already have an account?
        <button type="button" class="fd-linkbtn" @click="switchTo('signin')">Sign in</button>
      </template>
      <template v-else>
        New here?
        <button type="button" class="fd-linkbtn" @click="switchTo('register')">Create an account</button>
      </template>
    </p>

    <p v-if="mode === 'sent'" class="fdga__note">
      No link yet? Check your spam folder. Signing in below sends a fresh one if the address still
      isn't verified.
    </p>
  </section>
</template>

<style scoped>
.fd-linkbtn {
  border: none;
  background: none;
  padding: 0;
  color: #1a6b3c;
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}

.fdga {
  padding: 22px;
  border: 1px solid #e4e9e6;
  border-radius: 14px;
  background: #fff;
}

.fdga__head {
  display: flex;
  gap: 13px;
  align-items: flex-start;
  margin-bottom: 20px;
}

.fdga__badge {
  display: grid;
  place-items: center;
  flex: none;
  width: 40px;
  height: 40px;
  border-radius: 11px;
  background: #eefaf1;
  color: #1a6b3c;
}

.fdga__title { margin: 0 0 4px; font-size: 17px; font-weight: 800; letter-spacing: -0.01em; }

.fdga__sub { margin: 0; color: #5b6b62; font-size: 13.5px; line-height: 1.5; }

.fdga__form { display: block; }

.fdga__field { display: block; }
.fdga__field + .fdga__field { margin-top: 13px; }

.fdga__field > span {
  display: block;
  margin-bottom: 5px;
  color: #4a5b52;
  font-size: 12.5px;
  font-weight: 700;
}

.fdga__field em {
  font-style: normal;
  font-weight: 400;
  color: #9aa5a0;
}

.fdga__field input {
  width: 100%;
  padding: 11px 12px;
  border: 1px solid #d7ded9;
  border-radius: 10px;
  background: #fff;
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
  color: #1a1a1a;
}

.fdga__field input:focus { outline: 2px solid #1a6b3c; outline-offset: -1px; }
.fdga__field input:disabled { background: #f6f8f7; }

.fdga__field small {
  display: block;
  margin-top: 5px;
  color: #8b968f;
  font-size: 12px;
  line-height: 1.4;
}

/* The peek button sits inside the field, so the wrapper is the positioning
   context and the input needs room for it on the right. */
.fdga__secret { position: relative; display: block; }
.fdga__secret input { padding-right: 42px; }

.fdga__peek {
  position: absolute;
  top: 50%;
  right: 5px;
  display: grid;
  place-items: center;
  width: 31px;
  height: 31px;
  transform: translateY(-50%);
  border: none;
  border-radius: 8px;
  background: none;
  color: #7d8a84;
  cursor: pointer;
}

.fdga__peek:hover { color: #1a1a1a; }

.fdga__go {
  width: 100%;
  height: 46px;
  margin-top: 18px;
  border: none;
  border-radius: 999px;
  background: #22c55e;
  color: #06240f;
  font: 800 14.5px/1 inherit;
  cursor: pointer;
}

.fdga__go:hover:not(:disabled) { background: #16a34a; color: #fff; }
.fdga__go:disabled { opacity: 0.5; cursor: not-allowed; }

.fdga__alert {
  margin: 16px 0 0;
  padding: 11px 12px;
  border-radius: 10px;
  background: #fdf0ef;
  color: #8a2c22;
  font-size: 13px;
  line-height: 1.45;
}

.fdga__switch {
  margin: 16px 0 0;
  color: #5b6b62;
  font-size: 13.5px;
  text-align: center;
}

.fdga__note {
  margin: 14px 0 0;
  padding: 11px 12px;
  border-radius: 10px;
  background: #f4f6f5;
  color: #3d4a43;
  font-size: 12.5px;
  line-height: 1.45;
}
</style>
