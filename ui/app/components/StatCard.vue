<script setup lang="ts">
const props = defineProps({
  title: {
    type: String,
    required: true,
  },

  // data is an object with key value pairs to display
  data: {
    type: Object as () => Record<string, any>,
    required: true,
  },
});

// transform the data keys from camelCase to Title Case for display
const formattedData = computed(() => {
  const result: Record<string, any> = {};
  for (const [key, value] of Object.entries(props.data)) {
    const titleKey = key
      .replace(/([A-Z])/g, " $1")
      .replace(/^./, (str) => str.toUpperCase());
    result[titleKey] = value;
  }
  return result;
});
</script>
<template>
  <UCard class="p-0" v-if="data">
    <template #header>
      <div class="text-lg font-semibold">{{ title }}</div>
    </template>
    <div class="p">
      <div v-for="(value, key) in formattedData" :key="key" class="flex gap-2">
        <div class="font-medium text-muted">{{ key }}:</div>
        <div class="">{{ value }}</div>
      </div>
    </div>
  </UCard>
</template>
