const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

// Run esbuild via npx with safe node execution
const esbuildPath = require.resolve('esbuild', { paths: [__dirname] });
const esbuild = require(esbuildPath);

esbuild.buildSync({
  entryPoints: [path.join(__dirname, 'agentation-entry.js')],
  bundle: true,
  minify: false,
  format: 'iife',
  define: {
    'process.env.NODE_ENV': '"development"'
  },
  outfile: path.join(__dirname, 'web', 'agentation.bundle.js'),
});

console.log('✅ Agentation successfully bundled to web/agentation.bundle.js');
