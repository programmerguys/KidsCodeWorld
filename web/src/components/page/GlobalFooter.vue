<template>
  <div class="footer" v-html="$store.getters.sysConfig.footer">
  </div>
</template>

<script>
  export default {
    name: "LayoutFooter",
    data() {
      return {
        footer: this.$store.getters.sysConfig.brandName
      }
    },
  }
</script>

<style lang="less" scoped>
  .footer {
    padding: 0 16px;
    margin: 48px 0 24px;
    text-align: center;

    .links {
      margin-bottom: 8px;
      display: inline;
      a {
        color: rgba(0, 0, 0, .45);

        &:hover {
          color: rgba(0, 0, 0, .65);
        }

        &:not(:last-child) {
          margin-right: 40px;
        }
      }
    }
    .copyright {
      color: rgba(0, 0, 0, .45);
      font-size: 14px;
    }
  }
</style>