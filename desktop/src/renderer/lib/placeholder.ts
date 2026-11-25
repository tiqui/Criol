/**
 * Artwork placeholder for items without a cover. The same seed (item id) always gives
 * the same colors, so a song keeps "its" gradient everywhere, while different songs look varied.
 */
const hashString = (value: string) => {
    let hash = 2166136261;
    for (let i = 0; i < value.length; i += 1) {
        hash ^= value.charCodeAt(i);
        hash = Math.imul(hash, 16777619);
    }
    return hash >>> 0;
};

export const getPlaceholderGradient = (seed?: null | string) => {
    const hash = hashString(seed || 'criol');
    const hue = hash % 360;
    // Second hue 40-140 degrees away: varied but never clashing.
    const shift = 40 + ((hash >>> 9) % 100);
    const hue2 = (hue + ((hash >>> 17) & 1 ? shift : -shift) + 360) % 360;
    const angle = 90 + ((hash >>> 3) % 180);
    const saturation = 55 + ((hash >>> 11) % 25);

    // One in eight items gets a solid color instead of a gradient.
    if ((hash >>> 24) % 8 === 0) {
        return `hsl(${hue} ${saturation}% 42%)`;
    }

    return `linear-gradient(${angle}deg, hsl(${hue} ${saturation}% 48%), hsl(${hue2} ${saturation}% 30%))`;
};
