import React from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { configureAuth } from '../shared/api'
import '../shared/styles.css'

configureAuth('nottrilix.shop.token')

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
