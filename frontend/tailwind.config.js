/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{html,ts}"],
  theme: {
    extend: {
      colors: {
        'ab-primary':   '#0A3F6B',
        'ab-accent':    '#C8A84B',
        'ab-success':   '#1B7A4E',
        'ab-danger':    '#C5372A',
      },
      fontFamily: {
        sans: ['Inter', 'Segoe UI', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
