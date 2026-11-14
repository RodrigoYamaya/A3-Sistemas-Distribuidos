package com.ibmr.sdm.api_clinica.service;

import com.ibmr.sdm.api_clinica.Exceptions.RecursoNaoEncontradoException;
import com.ibmr.sdm.api_clinica.mapper.VacinaMapper;
import com.ibmr.sdm.api_clinica.model.dto.IntegracaoGovernoRequestDto;
import com.ibmr.sdm.api_clinica.model.dto.VacinaRequestDto;
import com.ibmr.sdm.api_clinica.model.dto.VacinaResponseDto;
import com.ibmr.sdm.api_clinica.model.entities.Animal;
import com.ibmr.sdm.api_clinica.model.entities.Vacina;
import com.ibmr.sdm.api_clinica.model.entities.Veterinario;
import com.ibmr.sdm.api_clinica.model.enums.TipoOperacaoIntegracao;
import com.ibmr.sdm.api_clinica.repository.AnimalRepository;
import com.ibmr.sdm.api_clinica.repository.VacinaRepository;
import com.ibmr.sdm.api_clinica.repository.VeterinarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class VacinaService {

    private final VacinaRepository vacinaRepository;
    private final VacinaMapper vacinaMapper;
    private final AnimalRepository animalRepository;
    private final VeterinarioRepository veterinarioRepository;
    private final IntegracaoGovernoService integracaoService;

    public VacinaService(VacinaRepository vacinaRepository, VacinaMapper vacinaMapper,
                         AnimalRepository animalRepository, VeterinarioRepository veterinarioRepository,
                         IntegracaoGovernoService integracaoService) {
        this.vacinaRepository = vacinaRepository;
        this.vacinaMapper = vacinaMapper;
        this.animalRepository = animalRepository;
        this.veterinarioRepository = veterinarioRepository;
        this.integracaoService = integracaoService;
    }

    @Transactional
    public VacinaResponseDto cadastrar(VacinaRequestDto dto) {
        Animal animal = buscarAnimal(dto.animalId());
        Veterinario veterinario = buscarVeterinario(dto.veterinarioId());

        Vacina vacina = vacinaMapper.toEntity(dto);
        vacina.setAnimal(animal);
        vacina.setVeterinario(veterinario);

        // A vacina é salva localmente primeiro (cumprindo a premissa obrigatória) que uma das regras do escopo
        Vacina vacinaSalva = vacinaRepository.save(vacina);

        // Dispara a integração apenas se for antirrábica que regra foi definida pelo escopo do trabalho
        if (vacinaSalva.getAntirrabica() != null && vacinaSalva.getAntirrabica()) {
            IntegracaoGovernoRequestDto integracaoDto = new IntegracaoGovernoRequestDto(
                    dto.animalId(),
                    vacinaSalva.getId(),
                    TipoOperacaoIntegracao.VACINA_ANTIRRABICA,
                    UUID.randomUUID().toString()
            );

            integracaoService.solicitarIntegracao(integracaoDto);
        }

        return vacinaMapper.toDto(vacinaSalva);
    }

    public List<VacinaResponseDto> listarTodas() {
        return vacinaRepository.findAll().stream()
                .map(vacinaMapper::toDto)
                .toList();
    }

    public List<VacinaResponseDto> listarPorVacinaId(Long animalId) {
        return vacinaRepository.findByAnimalId(animalId).stream()
                .map(vacinaMapper::toDto)
                .toList();
    }

    public VacinaResponseDto buscarPorId(Long id) {
        Vacina vacina = vacinaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vacina com ID " + id + " não encontrada"));
        return vacinaMapper.toDto(vacina);
    }

    @Transactional
    public VacinaResponseDto atualizar(Long id, VacinaRequestDto dto) {
        Vacina vacinaExistente = vacinaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vacina com ID " + id + " não encontrada"));

        Animal animal = buscarAnimal(dto.animalId());
        Veterinario veterinario = buscarVeterinario(dto.veterinarioId());

        Vacina vacinaAtualizada = vacinaMapper.toEntity(dto);
        vacinaAtualizada.setId(vacinaExistente.getId());
        vacinaAtualizada.setAnimal(animal);
        vacinaAtualizada.setVeterinario(veterinario);

        vacinaAtualizada = vacinaRepository.save(vacinaAtualizada);
        return vacinaMapper.toDto(vacinaAtualizada);
    }

    @Transactional
    public void excluir(Long id) {
        if (!vacinaRepository.existsById(id)) {
            throw new RecursoNaoEncontradoException("Vacina com ID " + id + " não encontrada");
        }
        vacinaRepository.deleteById(id);
    }

    private Animal buscarAnimal(Long animalId) {
        return animalRepository.findById(animalId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Animal com ID " + animalId + " não encontrado"));
    }

    private Veterinario buscarVeterinario(Long veterinarioId) {
        return veterinarioRepository.findById(veterinarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veterinário com ID " + veterinarioId + " não encontrado"));
    }
}