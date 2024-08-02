import {themes as prismThemes} from 'prism-react-renderer';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Prometheus4cats',
  tagline: 'Functional Prometheus Metrics API for Scala',
  favicon: 'img/favicon.ico',

  url: 'https://permutive-engineering.github.io',
  baseUrl: '/prometheus4cats/',
  trailingSlash: true,

  organizationName: 'permutive-engineering',
  projectName: 'prometheus4cats',

  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          path: 'target/mdoc',
          editUrl: params => 'https://github.com/permutive-engineering/prometheus4cats/edit/main/website/docs/' + params.docPath,
        },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      image: 'img/dsl.png',
      colorMode: {
        disableSwitch: true,
        respectPrefersColorScheme: true,
      },
      navbar: {
        title: 'Prometheus4cats',
        logo: {
          alt: 'Permutive Logo',
          src: 'img/logo.svg',
        },
        items: [
          {
            type: 'doc',
            docId: 'getting-started',
            position: 'left',
            label: 'Docs',
          },
          {
            href: 'https://permutive.com',
            position: 'left',
            label: 'Permutive',
          },
          {
            href: 'https://github.com/permutive-engineering/prometheus4cats',
            className: "header-github-link",
            "aria-label": "GitHub repository",
            position: 'right',
          },
        ],
      },
      footer: {
        logo: {
          alt: 'Permutive Logo',
          src: 'img/logo.svg',
          target: '_blank',
          href: 'https://permutive.com',
          width: 51,
          height: 51,
        },
        copyright: `Prometheus4Cats is a <a href="https://permutive.com" target="_blank">Permutive</a> project distributed under the Apache-2.0 license.`,
      },
      prism: {
        theme: prismThemes.github,
        darkTheme: prismThemes.dracula,
        additionalLanguages: ['java', 'scala'],
      },
    }),
};

module.exports = config;
