/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{vue,js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: "#6c63ff",
          dark: "#3f3d9e",
          light: "#f0eeff",
          lighter: "#e8e6ff",
        },
        admin: {
          DEFAULT: "#ff6b6b",
          dark: "#c44569",
          light: "#fff0f0",
        },
      },
    },
  },
  plugins: [],
  corePlugins: {
    preflight: false,
  },
};