import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { login, saveSession } from '../services/api';

/**
 * LoginPage — authenticates the user and stores the JWT token.
 *
 * On success: saves token + user to localStorage, redirects to dashboard.
 * On failure: shows error message from the backend.
 */
function LoginPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ username: '', password: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleChange = (e) =>
    setForm(prev => ({ ...prev, [e.target.name]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await login(form.username, form.password);
      const { token, username, email, preferredMarket } = res.data;
      saveSession(token, { username, email, preferredMarket });
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.message || 'Login failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        {/* Brand */}
        <div style={{ marginBottom: 32 }}>
          <div style={{ fontSize: 28, fontWeight: 800, marginBottom: 8 }}>
            Quant<span style={{ color: 'var(--accent-blue)' }}>View</span>
          </div>
          <div className="auth-title">Welcome back</div>
          <div className="auth-subtitle">
            Sign in to access your fintech analysis dashboard
          </div>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Username</label>
            <input
              className="form-input"
              type="text"
              name="username"
              value={form.username}
              onChange={handleChange}
              placeholder="Enter your username"
              required
              autoFocus
            />
          </div>

          <div className="form-group">
            <label className="form-label">Password</label>
            <input
              className="form-input"
              type="password"
              name="password"
              value={form.password}
              onChange={handleChange}
              placeholder="Enter your password"
              required
            />
          </div>

          {error && <div className="error-box" style={{ marginBottom: 16 }}>{error}</div>}

          <button
            type="submit"
            className="btn btn-primary btn-lg"
            style={{ width: '100%', marginTop: 8 }}
            disabled={loading}
          >
            {loading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>

        <div className="auth-divider">
          Don't have an account?{' '}
          <Link to="/register" className="auth-link">Create one</Link>
        </div>

        {/* Demo hint */}
        <div style={{
          background: 'rgba(59,130,246,0.08)',
          border: '1px solid rgba(59,130,246,0.2)',
          borderRadius: 8,
          padding: '12px 16px',
          fontSize: 13,
          color: 'var(--text-secondary)'
        }}>
          💡 First time? <Link to="/register" className="auth-link">Register</Link> to create an account.
          Select your preferred market (NASDAQ or FTSE) during registration.
        </div>
      </div>
    </div>
  );
}

export default LoginPage;
