import { LobbySignalField } from '../components/LobbySignalField'
import { SiteHeader } from '../components/SiteHeader'

export function StorePage() {
  return (
    <main className="portal-page store-page">
      <LobbySignalField />
      <SiteHeader />
      <section className="portal-content store-content">
        <span className="portal-code">SPIRIT CORE / STORE</span>
        <h1>Store</h1>
        <div className="store-message">
          <span className="store-sigil">◇</span>
          <p>请各位master期待下次更新喵～ (∠・ω&lt; )⌒☆</p>
        </div>
      </section>
    </main>
  )
}
