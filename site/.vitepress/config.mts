import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'jdbscript',
  description: 'Type-safe, zero-boilerplate database test state setup for modern Java',
  appearance: 'dark',
  themeConfig: {
    siteTitle: 'jdbscript',
    nav: [
      { text: 'Home', link: '/' },
      { text: 'Why JDBScript?', link: '/#why-jdbscript' },
      { text: 'VS DbUnit', link: '/#jdbscript-vs-dbunit' },
      { text: 'Quickstart', link: '/#quickstart' },
      { text: 'Recipes', link: '/#recipes' },
      {
        text: 'Maven Central: 1.3.0',
        link: 'https://central.sonatype.com/artifact/org.jdbscript/jdbscript/1.3.0'
      }
    ],
    socialLinks: [
      { icon: 'github', link: 'https://github.com/cyberdruid-dev/jdbscript' }
    ],
    footer: {
      message: 'Released under the Apache 2.0 License.',
      copyright: 'Copyright © cyberdruid-dev'
    }
  }
})
