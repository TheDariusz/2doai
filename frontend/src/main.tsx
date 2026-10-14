import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
// Side-effect import: initialises i18next (and `<html lang>`) before the first render.
import './i18n'
// Side-effect import: writes `data-theme` and tracks the OS while the choice is auto.
import './theme'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
