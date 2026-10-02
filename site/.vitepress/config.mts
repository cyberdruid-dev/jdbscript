import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'JDBScript — Type-Safe Database Test State Setup for Java',
  titleTemplate: ':title',
  description: 'Type-safe, zero-boilerplate database test fixtures and seeding for modern Java. Multi-DBMS support, automatic foreign key ordering, and zero XML or raw SQL.',
  cleanUrls: true,
  sitemap: {
    hostname: 'https://jdbscript.org'
  },
  head: [
    ['link', { rel: 'canonical', href: 'https://jdbscript.org/' }],
    ['meta', { name: 'keywords', content: 'java, database testing, dbunit alternative, testcontainers, junit 5, spring boot, database fixtures, database seeding, flyway testing, liquibase testing, test data setup' }],
    ['meta', { name: 'author', content: 'cyberdruid-dev' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:site_name', content: 'JDBScript' }],
    ['meta', { property: 'og:title', content: 'JDBScript — Type-Safe Database Test State Setup for Java' }],
    ['meta', { property: 'og:description', content: 'Replace brittle SQL scripts and XML datasets with compile-safe, fluent Java interfaces across 12+ database engines.' }],
    ['meta', { property: 'og:url', content: 'https://jdbscript.org/' }],
    ['meta', { name: 'twitter:card', content: 'summary' }],
    ['meta', { name: 'twitter:title', content: 'JDBScript — Type-Safe Database Test State Setup for Java' }],
    ['meta', { name: 'twitter:description', content: 'Type-safe, zero-boilerplate database test fixtures and seeding for modern Java.' }],
    [
      'script',
      { type: 'application/ld+json' },
      JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'SoftwareApplication',
        'name': 'JDBScript',
        'applicationCategory': 'DeveloperApplication',
        'operatingSystem': 'Cross-platform',
        'programmingLanguage': 'Java',
        'description': 'Type-safe, zero-boilerplate database test state setup for modern Java.',
        'url': 'https://jdbscript.org',
        'license': 'https://www.apache.org/licenses/LICENSE-2.0',
        'offers': {
          '@type': 'Offer',
          'price': '0',
          'priceCurrency': 'USD'
        }
      })
    ]
  ],
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
