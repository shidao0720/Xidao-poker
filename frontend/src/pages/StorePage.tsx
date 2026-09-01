import { useEffect, useMemo, useState, type CSSProperties } from 'react'
import { useNavigate } from 'react-router-dom'
import { accountApi, type CosmeticSlot, type StoreCategory, type StoreItem } from '../api/account'
import { LobbySignalField } from '../components/LobbySignalField'
import { SiteHeader } from '../components/SiteHeader'
import { useAccountStore } from '../store/accountStore'
import { loadoutValue } from '../utils/cosmetics'

type CatalogMode = 'store' | 'owned' | 'featured'
type CatalogCategory = 'ALL' | StoreCategory | 'PROFILE_STYLE'

const categories: Array<{ key: CatalogCategory; label: string; symbol: string }> = [
  { key: 'ALL', label: '全部陈列', symbol: '✦' },
  { key: 'AVATAR_FRAME', label: '头像框', symbol: '◈' },
  { key: 'CARD_BACK', label: '牌背', symbol: '▣' },
  { key: 'TITLE', label: '称号', symbol: '⌁' },
  { key: 'BUTTON_EFFECT', label: '局内按钮效果', symbol: '✧' },
  { key: 'VICTORY_EFFECT', label: '结算演出', symbol: '♛' },
  { key: 'PROFILE_STYLE', label: '主页风格', symbol: '◇' },
  { key: 'BUNDLE', label: '组合珍藏', symbol: '❖' },
]

const rarityLabels = { COMMON: '普通', RARE: '稀有', EPIC: '史诗', LEGENDARY: '传说' }
const rarityOrder = { COMMON: 1, RARE: 2, EPIC: 3, LEGENDARY: 4 }

function itemOwned(item: StoreItem, cosmetics: string[]) {
  return item.grants.every((key) => cosmetics.includes(key))
}

function itemEquipped(item: StoreItem, profile: NonNullable<ReturnType<typeof useAccountStore.getState>['profile']>) {
  return item.category !== 'BUNDLE' && loadoutValue(profile.loadout, item.category as CosmeticSlot) === item.key
}

export function StorePage() {
  const navigate = useNavigate()
  const profile = useAccountStore((state) => state.profile)
  const purchaseStoreItem = useAccountStore((state) => state.purchaseStoreItem)
  const equipCosmetic = useAccountStore((state) => state.equipCosmetic)
  const [catalog, setCatalog] = useState<StoreItem[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [mode, setMode] = useState<CatalogMode>('store')
  const [category, setCategory] = useState<CatalogCategory>('ALL')
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState('featured')
  const [selectedKey, setSelectedKey] = useState('grail-judgement')
  const [wished, setWished] = useState<Set<string>>(new Set())
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    accountApi.storeCatalog()
      .then((items) => {
        if (!active) return
        setCatalog(items)
        setSelectedKey((current) => items.some((item) => item.key === current) ? current : items[0]?.key ?? current)
      })
      .catch((error: unknown) => active && setNotice(error instanceof Error ? error.message : '商城目录加载失败'))
      .finally(() => active && setLoading(false))
    return () => { active = false }
  }, [])

  const selected = catalog.find((item) => item.key === selectedKey) ?? catalog[0]
  const visibleItems = useMemo(() => {
    if (!profile) return []
    let items = category === 'ALL' ? [...catalog] : catalog.filter((item) => item.category === category)
    if (mode === 'owned') items = items.filter((item) => itemOwned(item, profile.cosmetics))
    if (mode === 'featured') items = items.filter((item) => item.tags.some((tag) => ['FEATURED', 'SEASON', 'LIMITED', 'BEST'].includes(tag)))
    const normalized = query.trim().toLowerCase()
    if (normalized) items = items.filter((item) => [item.name, item.subtitle, item.series].join(' ').toLowerCase().includes(normalized))
    if (sort === 'low') items.sort((left, right) => left.priceCrystals - right.priceCrystals)
    if (sort === 'high') items.sort((left, right) => right.priceCrystals - left.priceCrystals)
    if (sort === 'rarity') items.sort((left, right) => rarityOrder[right.rarity] - rarityOrder[left.rarity])
    if (sort === 'featured') items.sort((left, right) => right.tags.length - left.tags.length || rarityOrder[right.rarity] - rarityOrder[left.rarity])
    return items
  }, [catalog, category, mode, profile, query, sort])

  function selectMode(next: CatalogMode) {
    setMode(next)
    setCategory('ALL')
  }

  async function purchaseOrEquip() {
    if (!profile || !selected || busy) return
    setBusy(true)
    setNotice(null)
    try {
      const owned = itemOwned(selected, profile.cosmetics)
      if (!owned) {
        await purchaseStoreItem(selected.key)
        setNotice(`${selected.name} 已记录到你的灵基收藏`)
      } else if (selected.category !== 'BUNDLE') {
        const slot = selected.category as CosmeticSlot
        const equipped = itemEquipped(selected, profile)
        await equipCosmetic(slot, equipped ? null : selected.key)
        setNotice(equipped ? `${selected.name} 已卸下` : `${selected.name} 已装备`)
      }
    } catch (error) {
      setNotice(error instanceof Error ? error.message : '操作未能完成')
    } finally {
      setBusy(false)
    }
  }

  if (!profile) return null
  const ownedCount = catalog.filter((item) => itemOwned(item, profile.cosmetics)).length
  const selectedOwned = selected ? itemOwned(selected, profile.cosmetics) : false
  const selectedEquipped = selected ? itemEquipped(selected, profile) : false
  const insufficient = Boolean(selected && !selectedOwned && profile.wallet.spiritCrystals < selected.priceCrystals)
  const actionLabel = !selected ? '选择灵基' : selectedOwned
    ? selected.category === 'BUNDLE' ? '已拥有组合内容' : selectedEquipped ? '解除装备' : '装备灵基'
    : insufficient ? '英魂结晶不足' : '获取灵基'

  return (
    <main className="portal-page store-page market-page">
      <LobbySignalField />
      <SiteHeader />
      <section className="portal-content market-content">
        <section className="market-hero">
          <div>
            <span className="portal-code">SPIRIT ARCHIVE / SEASON 00</span>
            <h3>暂时只展示部分效果<br /><em>请各位master期待下次更新喵～(∠・ω&lt; )⌒</em></h3>
            <p>所有陈列均为纯外观收藏，不会改变牌局胜率、行动规则或筹码收益。</p>
            <div className="market-hero-actions">
              <button onClick={() => { setSelectedKey('avalon'); setMode('featured') }}>查看赛季珍藏 <span>→</span></button>
              <button onClick={() => selectMode('owned')}>我的收藏 <b>{ownedCount}</b></button>
            </div>
          </div>
          <div className="market-relic" aria-hidden="true">
            <i /><i /><strong>F</strong>
            <span><small>SEASON RELIC / 00</small><b>AVALON</b><em>胜利结算演出</em></span>
          </div>
        </section>

        <section className="market-tabs" aria-label="商城区域">
          <button className={mode === 'store' ? 'is-active' : ''} onClick={() => selectMode('store')}><span>01</span>灵基陈列</button>
          <button className={mode === 'owned' ? 'is-active' : ''} onClick={() => selectMode('owned')}><span>02</span>我的收藏 <b>{ownedCount}</b></button>
          <button className={mode === 'featured' ? 'is-active' : ''} onClick={() => selectMode('featured')}><span>03</span>赛季精选</button>
          <p><i>◇</i><span><strong>PLAY-MONEY ONLY</strong><small>无现金价值 · 不可交易 · 不可转赠</small></span></p>
        </section>

        <section className="market-workspace">
          <aside className="market-sidebar">
            <header><small>CATALOG</small><strong>灵基分类</strong></header>
            <div className="market-categories">
              {categories.map((entry) => {
                const count = entry.key === 'ALL' ? catalog.length : catalog.filter((item) => item.category === entry.key).length
                return <button className={category === entry.key ? 'is-active' : ''} key={entry.key} onClick={() => { setCategory(entry.key); setMode('store') }}><span>{entry.symbol}</span>{entry.label}<b>{count}</b></button>
              })}
            </div>
            <div className="market-conversion">
              <span>◇</span><small>SPIRIT CONVERSION</small><strong>10 筹码 = 1 英魂结晶</strong>
              <p>单向兑换，兑换后不可还原。建议预留下一次牌桌买入。</p>
              <button onClick={() => navigate('/profile')}>前往个人主页兑换</button>
            </div>
          </aside>

          <section className="market-catalog">
            <header className="market-toolbar">
              <div><span className="portal-code">CATALOG / {category}</span><h2>{mode === 'owned' ? '我的收藏' : mode === 'featured' ? '赛季精选' : categories.find((entry) => entry.key === category)?.label}</h2></div>
              <label><span>⌕</span><input type="search" name="storeSearch" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索外观、称号或系列" /></label>
              <select name="storeSort" aria-label="商品排序" value={sort} onChange={(event) => setSort(event.target.value)}><option value="featured">精选优先</option><option value="low">价格从低到高</option><option value="high">价格从高到低</option><option value="rarity">稀有度</option></select>
            </header>
            <div className="market-result"><span>{visibleItems.length} 件灵基</span><span><i />普通 <i />稀有 <i />史诗 <i />传说</span></div>
            {loading ? <div className="market-empty"><span>◇</span><strong>正在读取灵基档案</strong></div> : visibleItems.length === 0 ? (
              <div className="market-empty"><span>◇</span><strong>{category === 'PROFILE_STYLE' ? '主页风格暂未开放' : '没有找到对应灵基'}</strong><p>{category === 'PROFILE_STYLE' ? '该分类会在后续版本加入，敬请期待。' : '尝试更换分类或搜索词。'}</p></div>
            ) : (
              <div className="market-grid">
                {visibleItems.map((item) => {
                  const owned = itemOwned(item, profile.cosmetics)
                  const equipped = itemEquipped(item, profile)
                  return (
                    <button className={`market-card${selected?.key === item.key ? ' is-selected' : ''}`} key={item.key} onClick={() => setSelectedKey(item.key)}>
                      <div className="market-card-art" data-category={item.category} style={{ '--item-color': item.color } as CSSProperties}>
                        <span className="market-tags">{item.tags.map((tag) => <i key={tag}>{tag}</i>)}</span>
                        {owned && <b className="market-owned">✓</b>}<strong>{item.glyph}</strong>
                      </div>
                      <div className="market-card-copy"><small><span>{item.series}</span><b data-rarity={item.rarity}>{rarityLabels[item.rarity]}</b></small><h3>{item.name}</h3><p>{item.subtitle}</p><footer><strong><i>◇</i>{item.priceCrystals.toLocaleString()}</strong><span className={equipped ? 'is-equipped' : ''}>{equipped ? '装备中' : owned ? '已拥有' : '查看详情'}</span></footer></div>
                    </button>
                  )
                })}
              </div>
            )}
          </section>

          <aside className="market-detail">
            {selected && <>
              <div className="market-detail-art" style={{ '--item-color': selected.color } as CSSProperties}><i /><i /><strong>{selected.glyph}</strong><small>LIVE PREVIEW</small></div>
              <div className="market-detail-copy"><header><span>{selected.series}</span><b data-rarity={selected.rarity}>{rarityLabels[selected.rarity]}</b></header><h2>{selected.name}</h2><p className="market-detail-subtitle">{selected.subtitle}</p><p>{selected.description}</p><ul>{selected.features.map((feature) => <li key={feature}>{feature}</li>)}</ul><div className="market-price"><span><small>需要英魂结晶</small><strong><i>◇</i>{selected.priceCrystals.toLocaleString()}</strong></span><button disabled={busy || insufficient || (selected.category === 'BUNDLE' && selectedOwned)} onClick={purchaseOrEquip}>{busy ? '处理中…' : actionLabel}</button><button className={wished.has(selected.key) ? 'is-wished' : ''} aria-label="收藏商品" onClick={() => setWished((current) => { const next = new Set(current); if (next.has(selected.key)) next.delete(selected.key); else next.add(selected.key); return next })}>{wished.has(selected.key) ? '♥' : '♡'}</button></div><small className="market-permanent">{selectedOwned ? '已记录于你的灵基收藏' : '永久拥有 · 购买后可立即装备'}</small></div>
            </>}
          </aside>
        </section>
        {notice && <div className="market-notice" role="status"><span>◇</span><p>{notice}</p><button onClick={() => setNotice(null)}>×</button></div>}
      </section>
    </main>
  )
}
