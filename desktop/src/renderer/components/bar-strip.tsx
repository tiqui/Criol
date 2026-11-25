import clsx from 'clsx';

import styles from './bar-strip.module.css';

interface BarStripProps {
    values: number[];
    labels: string[];
    /** Texto do valor (tooltip e leitor de tela). */
    format: (value: number) => string;
    /** Índice destacado (ex.: hora atual). */
    highlight?: number;
    /** Mostra só alguns rótulos no eixo (a cada N). */
    labelEvery?: number;
    height?: number;
    title: string;
}

/** Gráfico de barras simples (uma série): minutos por hora, por dia da semana, por dia do mês. */
export function BarStrip({ format, height = 120, highlight, labelEvery = 1, labels, title, values }: BarStripProps) {
    const max = Math.max(1, ...values);
    return (
        <figure aria-label={title} className={styles.figure}>
            <div className={styles.bars} style={{ height }}>
                {values.map((v, i) => (
                    <div className={styles.slot} key={i} title={`${labels[i]}: ${format(v)}`}>
                        <div
                            aria-label={`${labels[i]}: ${format(v)}`}
                            className={clsx(styles.bar, i === highlight && styles.highlight, v === 0 && styles.zero)}
                            role="img"
                            style={{ height: `${Math.max(v > 0 ? 4 : 2, (v / max) * 100)}%` }}
                        />
                    </div>
                ))}
            </div>
            <div className={styles.axis}>
                {labels.map((l, i) => (
                    <span key={i}>{i % labelEvery === 0 ? l : ''}</span>
                ))}
            </div>
        </figure>
    );
}
