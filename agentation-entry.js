import React from 'react';
import ReactDOM from 'react-dom/client';
import { Agentation } from 'agentation';

document.addEventListener('DOMContentLoaded', () => {
  let container = document.getElementById('agentation-root');
  if (!container) {
    container = document.createElement('div');
    container.id = 'agentation-root';
    document.body.appendChild(container);
  }

  const root = ReactDOM.createRoot(container);
  root.render(React.createElement(Agentation, { endpoint: 'http://localhost:4747' }));
  console.log('🛡️ Agentation toolbar mounted and connected to http://localhost:4747');
});
