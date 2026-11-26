import { describe, expect, it } from 'vitest';

import { placeMenu } from './menu-position';

const view = { height: 768, width: 1366 };
const menu = { height: 300, width: 240 };

describe('posição dos menus', () => {
    it('submenu vira para a esquerda quando não cabe à direita', () => {
        // "Adicionar à playlist" num menu encostado na borda direita.
        const anchor = { bottom: 284, left: 1104, right: 1342, top: 252 };
        const pos = placeMenu(menu, view, { anchor, kind: 'side' });
        expect(pos.left + menu.width).toBeLessThanOrEqual(anchor.left);
        expect(pos.left).toBeGreaterThanOrEqual(8);
    });

    it('submenu sobe quando passaria do fim da tela', () => {
        // "Velocidade" perto da parte de baixo da janela.
        const anchor = { bottom: 640, left: 495, right: 700, top: 606 };
        const pos = placeMenu(menu, view, { anchor, kind: 'side' });
        expect(pos.top + menu.height).toBeLessThanOrEqual(view.height - 8);
        expect(pos.left).toBe(704);
    });

    it('menu de botão abre acima quando não cabe embaixo', () => {
        const anchor = { bottom: 680, left: 450, right: 490, top: 640 };
        const pos = placeMenu({ height: 120, width: 220 }, view, { anchor, kind: 'below' });
        expect(pos.top + 120).toBeLessThanOrEqual(anchor.top);
    });

    it('clique direito no canto fica dentro da janela', () => {
        const pos = placeMenu(menu, view, { kind: 'point', x: 1360, y: 760 });
        expect(pos.left + menu.width).toBeLessThanOrEqual(view.width - 8);
        expect(pos.top + menu.height).toBeLessThanOrEqual(view.height - 8);
    });

    it('menu maior que a janela começa no topo (e rola)', () => {
        const pos = placeMenu({ height: 900, width: 240 }, view, { kind: 'point', x: 100, y: 400 });
        expect(pos.top).toBe(8);
    });
});
