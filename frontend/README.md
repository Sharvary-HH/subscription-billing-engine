# Console

Angular 19, standalone components, signals, typed reactive forms. Talks to the API at `/api`;
`npm start` proxies that to `http://localhost:8080` (see `proxy.conf.json`), and the Docker
image proxies it through nginx.

```bash
npm install
npm start          # http://localhost:4200
npm run build      # dist/frontend/browser
```

Palette and type are in `src/styles.scss`: ink `#141414`, slate `#444444`, grey `#979797`,
mist `#D6D6D6`, acid `#E2E800`, white; Mukta Malar throughout.
