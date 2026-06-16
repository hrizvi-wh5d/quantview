import React from 'react';
import { useNavigate } from 'react-router-dom';
import { getUser, logout } from '../services/api';

function Navbar() {
  const navigate = useNavigate();
  const user = getUser();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <nav className="navbar">
      <a className="nav-brand" href="/">
        Quant<span>View</span>
        <span style={{
          fontSize: 11,
          fontWeight: 500,
          color: 'var(--text-muted)',
          background: 'var(--bg-tertiary)',
          padding: '2px 8px',
          borderRadius: 4,
          marginLeft: 4
        }}>
          FINTECH EDITION
        </span>
      </a>

      <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
        {user && (
          <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
            <span style={{ color: 'var(--text-muted)' }}>Signed in as </span>
            <span style={{ color: 'var(--text-primary)', fontWeight: 500 }}>
              {user.username}
            </span>
            <span style={{
              marginLeft: 8,
              fontSize: 11,
              background: 'var(--bg-tertiary)',
              padding: '2px 8px',
              borderRadius: 4,
              color: 'var(--accent-blue)'
            }}>
              {user.preferredMarket}
            </span>
          </div>
        )}
        <button className="btn btn-secondary btn-sm" onClick={handleLogout}>
          Sign Out
        </button>
      </div>
    </nav>
  );
}

export default Navbar;
