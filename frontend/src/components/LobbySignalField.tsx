import type { CSSProperties } from 'react'

const signalTokens = ['A', '7', '♠', '10', 'Q', '3', '♥', 'K', '2', '9', '♦', 'J', '5', '♣', '8', '4']
const signalTones = ['cyan', 'white', 'cyan', 'red', 'blue'] as const
const glyphTokens = ['A', '♠', '7', '♥', 'K', '♦', '10', '♣']

function signalStyle(values: Record<string, string>): CSSProperties {
  return values as CSSProperties
}

export function LobbySignalField() {
  return (
    <div className="lobby-signal-field" aria-hidden="true">
      <div className="lobby-signal-noise" />
      <div className="lobby-signal-scan" />
      <div className="lobby-signal-columns">
        {Array.from({ length: 22 }, (_, index) => (
          <span
            className={`lobby-signal-column tone-${signalTones[index % signalTones.length]}`}
            key={index}
            style={signalStyle({
              '--signal-x': `${(index * 9.7 + (index % 3) * 2.4) % 103}%`,
              '--signal-size': `${6 + (index % 5) * 2}px`,
              '--signal-speed': `${16 + (index % 7) * 3}s`,
              '--signal-delay': `${-((index * 2.3) % 18)}s`,
              '--signal-opacity': `${0.18 + (index % 4) * 0.09}`,
            })}
          >
            {Array.from(
              { length: 21 + (index % 7) },
              (_, tokenIndex) => signalTokens[(index * 5 + tokenIndex * 3) % signalTokens.length],
            ).join('\n')}
          </span>
        ))}
      </div>
      <div className="lobby-signal-glyphs">
        {glyphTokens.map((glyph, index) => (
          <span
            className={`lobby-signal-glyph${index % 3 === 1 ? ' is-red' : ''}`}
            key={`${glyph}-${index}`}
            style={signalStyle({
              '--glyph-x': `${8 + ((index * 17) % 82)}%`,
              '--glyph-y': `${7 + ((index * 23) % 78)}%`,
              '--glyph-size': `${58 + (index % 4) * 38}px`,
              '--glyph-rotate': `${-18 + index * 7}deg`,
            })}
          >
            {glyph}
          </span>
        ))}
      </div>
    </div>
  )
}
