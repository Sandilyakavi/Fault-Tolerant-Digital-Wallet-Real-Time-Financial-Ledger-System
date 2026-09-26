import { createContext, useContext, useState, useEffect } from 'react'
import api from '../api/client.js'
import { getErrorMessage } from '../utils/helpers.js'

const AuthContext = createContext(null)

const MAPPING_KEY = 'payvault_user_mapping'

/**
 * Extract username from JWT subject if available.
 */
function extractUsernameFromJwt(token) {
  try {
    const parts = token.split('.')
    if (parts.length < 2) return null
    const payload = JSON.parse(atob(parts[1]))
    return payload.sub || payload.username || null
  } catch {
    return null
  }
}

/**
 * Read the persistent username→userId mapping from localStorage.
 * Returns a plain object, e.g. { "kavi": 1 }
 */
function getUserMapping() {
  try {
    const raw = localStorage.getItem(MAPPING_KEY) || localStorage.getItem('pv_user_mapping')
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

/** Persist a single username→userId pair into the mapping. */
function saveUserMapping(username, userId) {
  const map = getUserMapping()
  map[username] = Number(userId)
  localStorage.setItem(MAPPING_KEY, JSON.stringify(map))
}

/** Remove a single username entry from the mapping. */
function removeUserMapping(username) {
  const map = getUserMapping()
  delete map[username]
  localStorage.setItem(MAPPING_KEY, JSON.stringify(map))
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem('pv_token'))
  const [userId, setUserId] = useState(() => {
    const stored = localStorage.getItem('pv_userId')
    return stored ? Number(stored) : null
  })
  const [username, setUsername] = useState(() => localStorage.getItem('pv_username'))
  const [loading, setLoading] = useState(false)
  const [initializing, setInitializing] = useState(false)

  const isAuthenticated = !!token
  const hasProfile = !!userId

  useEffect(() => {
    if (token) {
      localStorage.setItem('pv_token', token)
    } else {
      localStorage.removeItem('pv_token')
    }
  }, [token])

  useEffect(() => {
    if (userId) {
      localStorage.setItem('pv_userId', String(userId))
    } else {
      localStorage.removeItem('pv_userId')
    }
  }, [userId])

  useEffect(() => {
    if (username) {
      localStorage.setItem('pv_username', username)
    } else {
      localStorage.removeItem('pv_username')
    }
  }, [username])

  /**
   * Attempt to restore a userId from the persistent mapping or by matching against
   * the live user list via GET /api/users.
   * Returns the valid userId or null.
   */
  const resolveUserId = async (forUsername, jwt) => {
    const authHeader = { Authorization: `Bearer ${jwt}` }
    const map = getUserMapping()
    const storedId = map[forUsername]

    if (storedId) {
      try {
        await api.get(`/api/users/${storedId}`, { headers: authHeader })
        await api.get(`/api/wallets/user/${storedId}`, { headers: authHeader })
        return storedId
      } catch (err) {
        // Only remove a mapping when the backend explicitly confirms that the mapped user does not exist (404)
        if (err.response?.status === 404) {
          removeUserMapping(forUsername)
        }
      }
    }

    // Dynamic resolution: fetch users list and match against username / email / name
    try {
      const usersRes = await api.get('/api/users', { headers: authHeader })
      const users = Array.isArray(usersRes.data) ? usersRes.data : []
      const term = (forUsername || '').trim().toLowerCase()

      const matched = users.find((u) => {
        const email = (u.email || '').toLowerCase()
        const emailPrefix = email.split('@')[0]
        const name = (u.name || '').toLowerCase()
        const nameCompact = name.replace(/\s+/g, '')

        return (
          email === term ||
          emailPrefix === term ||
          name === term ||
          nameCompact === term ||
          nameCompact.includes(term) ||
          term.includes(nameCompact) ||
          String(u.id) === term
        )
      })

      if (matched) {
        saveUserMapping(forUsername, matched.id)
        return matched.id
      }
    } catch {
      // Backend request failed or user service unavailable
    }

    return null
  }

  const login = async (loginUsername, password) => {
    setLoading(true)
    setInitializing(true)
    try {
      const res = await api.post('/api/auth/login', {
        username: loginUsername,
        password,
      })
      const jwt = res.data
      const authUser = extractUsernameFromJwt(jwt) || loginUsername

      // Resolve and validate userId BEFORE updating isAuthenticated in React state
      const resolved = await resolveUserId(authUser, jwt)

      // Synchronously write to localStorage
      localStorage.setItem('pv_token', jwt)
      localStorage.setItem('pv_username', authUser)
      if (resolved) {
        localStorage.setItem('pv_userId', String(resolved))
      } else {
        localStorage.removeItem('pv_userId')
      }

      // Atomically update React state together so token and userId are set in the exact same render
      setToken(jwt)
      setUsername(authUser)
      setUserId(resolved)

      return { success: true, hasProfile: !!resolved }
    } catch (err) {
      return { success: false, message: getErrorMessage(err) }
    } finally {
      setLoading(false)
      setInitializing(false)
    }
  }

  const signup = async (signupUsername, password) => {
    setLoading(true)
    try {
      await api.post('/api/auth/signup', {
        username: signupUsername,
        password,
      })
      return { success: true }
    } catch (err) {
      return { success: false, message: getErrorMessage(err) }
    } finally {
      setLoading(false)
    }
  }

  const completeProfile = async (name, email, phone) => {
    setLoading(true)
    try {
      const userRes = await api.post('/api/users', { name, email, phone })
      const newUserId = userRes.data.id

      await api.post('/api/wallets', { userId: newUserId })

      setUserId(newUserId)
      localStorage.setItem('pv_userId', String(newUserId))

      // Persist the mapping so it survives logout/re-login
      const currentUsername = username || localStorage.getItem('pv_username')
      if (currentUsername) {
        saveUserMapping(currentUsername, newUserId)
      }

      return { success: true }
    } catch (err) {
      return { success: false, message: getErrorMessage(err) }
    } finally {
      setLoading(false)
    }
  }

  const setExistingUserId = (id) => {
    setUserId(id)
  }

  const logout = () => {
    // Clear session state but KEEP the pv_user_mapping
    setToken(null)
    setUserId(null)
    setUsername(null)
    localStorage.removeItem('pv_token')
    localStorage.removeItem('pv_userId')
    localStorage.removeItem('pv_username')
    // NOTE: pv_user_mapping is intentionally NOT removed
  }

  return (
    <AuthContext.Provider
      value={{
        token,
        userId,
        username,
        isAuthenticated,
        hasProfile,
        loading,
        initializing,
        login,
        signup,
        completeProfile,
        setExistingUserId,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
