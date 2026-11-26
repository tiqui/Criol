/** Retângulo na tela (o mesmo formato de getBoundingClientRect). */
export interface Box {
    top: number;
    bottom: number;
    left: number;
    right: number;
}

export interface Size {
    width: number;
    height: number;
}

/**
 * Onde o menu abre:
 * - `point`: no ponto do clique (botão direito), para baixo e para a direita;
 * - `below`: abaixo de um botão, alinhado pela direita dele;
 * - `side`: ao lado de um item (submenu), à direita.
 * Em todos, se não couber, vira para o outro lado; e no fim é empurrado para dentro da
 * janela, para nunca ficar cortado.
 */
export type Placement = { kind: 'below'; anchor: Box } | { kind: 'point'; x: number; y: number } | { kind: 'side'; anchor: Box };

export const MENU_MARGIN = 8;
const GAP = 4;

const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(value, max));

export function placeMenu(menu: Size, view: Size, at: Placement): { left: number; top: number } {
    const m = MENU_MARGIN;
    const fitsRight = (left: number) => left + menu.width <= view.width - m;
    const fitsBelow = (top: number) => top + menu.height <= view.height - m;
    let left: number;
    let top: number;

    switch (at.kind) {
        case 'point': {
            left = fitsRight(at.x) ? at.x : at.x - menu.width;
            top = fitsBelow(at.y) ? at.y : at.y - menu.height;
            break;
        }
        case 'below': {
            const { anchor } = at;
            left = anchor.right - menu.width;
            if (left < m) left = anchor.left;
            top = anchor.bottom + GAP;
            // Sem espaço embaixo: abre acima do botão (se lá couber mais).
            if (!fitsBelow(top) && anchor.top - GAP - menu.height >= m) top = anchor.top - GAP - menu.height;
            break;
        }
        case 'side': {
            const { anchor } = at;
            left = anchor.right + GAP;
            if (!fitsRight(left)) left = anchor.left - GAP - menu.width;
            // Alinha o primeiro item com o item que abriu; se passar do fim da tela, sobe.
            top = anchor.top - 6;
            break;
        }
    }

    return {
        left: clamp(left, m, Math.max(m, view.width - m - menu.width)),
        top: clamp(top, m, Math.max(m, view.height - m - menu.height)),
    };
}

export const toBox = (rect: DOMRect): Box => ({ bottom: rect.bottom, left: rect.left, right: rect.right, top: rect.top });
