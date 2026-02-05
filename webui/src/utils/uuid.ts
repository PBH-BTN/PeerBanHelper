const GenerateUUID = async () => {
  if (crypto.randomUUID) {
    return crypto.randomUUID()
  } else {
    const uuid = await import('uuid')
    return uuid.v4()
  }
}

export default GenerateUUID
